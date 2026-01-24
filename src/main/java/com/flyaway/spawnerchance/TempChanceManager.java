package com.flyaway.spawnerchance;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TempChanceManager {

    private final SpawnerChance plugin;
    private final File tempChancesFile;
    private FileConfiguration tempChancesConfig;
    private final ConfigManager configManager;
    private LuckPerms luckPerms;
    private BukkitRunnable updateTask;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();

    public TempChanceManager(SpawnerChance plugin) {
        this.plugin = plugin;
        this.tempChancesFile = new File(plugin.getDataFolder(), "tempchances.yml");
        this.configManager = plugin.getConfigManager();
        initLuckPerms();
        loadTempChances();
        startBossBarUpdateTask();
    }

    private void startBossBarUpdateTask() {
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllBossBars();
            }
        };
        updateTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void initLuckPerms() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            RegisteredServiceProvider<LuckPerms> provider =
                    plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPerms = provider.getProvider();
                plugin.getLogger().info("LuckPerms API detected and in use.");
            } else {
                plugin.getLogger().warning("LuckPerms installed, but the provider was not found!");
            }
        } else {
            plugin.getLogger().warning("LuckPerms not detected! Temporary rights will not work.");
        }
    }

    public void loadTempChances() {
        if (!tempChancesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                tempChancesFile.createNewFile();
                plugin.getLogger().info("A new file has been created tempchances.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create tempchances.yml: " + e.getMessage());
            }
        }
        tempChancesConfig = YamlConfiguration.loadConfiguration(tempChancesFile);
        if (!tempChancesConfig.contains("players")) {
            tempChancesConfig.createSection("players");
            saveTempChances();
        }
        cleanupExpiredChances();
    }

    public void saveTempChances() {
        try {
            tempChancesConfig.save(tempChancesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save tempchances.yml: " + e.getMessage());
        }
    }

    public boolean addTempChance(Player player, int chance, String executor) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        long duration = configManager.getTempChanceDuration();
        long expiryTime = System.currentTimeMillis() + duration * 60 * 1000;

        int currentChance = plugin.getDropChance(player);
        if (currentChance > chance) {
            return false;
        }
        String path = "players." + playerId;
        if (tempChancesConfig.contains(path)) removeTempPermission(playerId, tempChancesConfig.getInt(path + ".chance", 0));

        tempChancesConfig.set(path + ".name", playerName);
        tempChancesConfig.set(path + ".chance", chance);
        tempChancesConfig.set(path + ".expiry", expiryTime);
        tempChancesConfig.set(path + ".given_by", executor);
        tempChancesConfig.set(path + ".given_at", System.currentTimeMillis());
        saveTempChances();

        boolean success = applyTempPermission(playerId, chance);
        if (success) startBossBarForPlayer(player, chance, expiryTime);
        return success;
    }

    private boolean applyTempPermission(UUID playerId, int chance) {
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms is not available, it is impossible to grant a temporary permission");
            return false;
        }

        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(playerId);
        try {
            User user = userFuture.join();
            if (user == null) {
                plugin.getLogger().warning("Couldn't upload user: " + playerId);
                return false;
            }

            String permission = "spawner.dropchance." + chance;
            Node node = Node.builder(permission).value(true).build();
            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);
            plugin.getLogger().info("A temporary permission has been granted " + permission + " for " + playerId);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error when granting a temporary permission: " + e.getMessage());
            return false;
        }
    }

    public boolean removeTempPermission(UUID playerId, int chance) {
        if (luckPerms == null) return false;
        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(playerId);
        try {
            User user = userFuture.join();
            if (user == null) return false;
            String permission = "spawner.dropchance." + chance;

            for (Node node : user.getNodes()) {
                if (node.getKey().equals(permission)) {
                    user.data().remove(node);
                    luckPerms.getUserManager().saveUser(user);
                    plugin.getLogger().info("Temporary permission removed " + permission + " for " + playerId);
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error when deleting a temporary permission: " + e.getMessage());
            return false;
        }
    }

    public int cleanupExpiredChances() {
        List<String> toRemove = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;

        if (tempChancesConfig.contains("players")) {
            ConfigurationSection playersSection = tempChancesConfig.getConfigurationSection("players");
            if (playersSection != null) {
                for (String playerId : playersSection.getKeys(false)) {
                    long expiry = tempChancesConfig.getLong("players." + playerId + ".expiry");
                    if (expiry < currentTime) {
                        UUID uuid = UUID.fromString(playerId);
                        int chance = tempChancesConfig.getInt("players." + playerId + ".chance");
                        if (removeTempPermission(uuid, chance)) {
                            toRemove.add(playerId);
                            removedCount++;
                            removeBossBar(uuid);
                        }
                    }
                }
            }
            for (String playerId : toRemove) tempChancesConfig.set("players." + playerId, null);
            if (removedCount > 0) {
                saveTempChances();
                plugin.getLogger().info("Automatically deleted " + removedCount + " expired temporary permissions");
            }
        }

        return removedCount;
    }

    private void startBossBarForPlayer(Player player, int chance, long expiryTime) {
        UUID playerId = player.getUniqueId();
        removeBossBar(playerId);

        String initialTitle = configManager.getMessage("bossbar-initial-title").replace("{chance}", String.valueOf(chance));
        BossBar bossBar = BossBar.bossBar(
                miniMessage.deserialize(initialTitle),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );
        bossBar.addViewer(player);
        activeBossBars.put(playerId, bossBar);

        updateBossBarProgress(playerId, bossBar, expiryTime);
    }

    private void updateAllBossBars() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, BossBar> entry : new HashMap<>(activeBossBars).entrySet()) {
            UUID playerId = entry.getKey();
            BossBar bossBar = entry.getValue();
            String path = "players." + playerId;
            if (!tempChancesConfig.contains(path)) {
                removeBossBar(playerId);
                continue;
            }
            long expiryTime = tempChancesConfig.getLong(path + ".expiry");
            int chance = tempChancesConfig.getInt(path + ".chance");
            if (expiryTime < currentTime) {
                removeBossBar(playerId);
                continue;
            }
            updateBossBarProgress(playerId, bossBar, expiryTime, chance);
        }
    }

    private void updateBossBarProgress(UUID playerId, BossBar bossBar, long expiryTime) {
        updateBossBarProgress(playerId, bossBar, expiryTime, tempChancesConfig.getInt("players." + playerId + ".chance", 0));
    }

    private void updateBossBarProgress(UUID playerId, BossBar bossBar, long expiryTime, int chance) {
        long currentTime = System.currentTimeMillis();
        long timeLeft = expiryTime - currentTime;
        long totalDuration = configManager.getTempChanceDuration() * 60 * 1000L;
        double progress = Math.max(0.0, Math.min(1.0, (double) timeLeft / totalDuration));
        bossBar.progress((float) progress);

        String timeText;
        BossBar.Color color;

        if (timeLeft > 60 * 1000) {
            long minutesLeft = timeLeft / 60 * 1000;
            timeText = configManager.getMessage("bossbar-minutes-left")
                    .replace("{chance}", String.valueOf(chance))
                    .replace("{minutes}", String.valueOf(minutesLeft));
            color = minutesLeft > 5 ? BossBar.Color.GREEN : BossBar.Color.YELLOW;
        } else {
            long secondsLeft = timeLeft / 1000;
            timeText = configManager.getMessage("bossbar-seconds-left")
                    .replace("{chance}", String.valueOf(chance))
                    .replace("{seconds}", String.valueOf(secondsLeft));
            color = BossBar.Color.RED;
        }

        bossBar.name(miniMessage.deserialize(timeText));
        bossBar.color(color);

        if (timeLeft <= 0) removeBossBar(playerId);
    }

    private void removeBossBar(UUID playerId) {
        BossBar bossBar = activeBossBars.remove(playerId);
        if (bossBar != null) {
            List<Player> viewers = new ArrayList<>();
            for (var viewer : bossBar.viewers()) {
                if (viewer instanceof Player player) {
                    viewers.add(player);
                }
            }
            for (Player player : viewers) {
                bossBar.removeViewer(player);
            }
        };
    }

    public void onPlayerQuit(Player player) {
        removeBossBar(player.getUniqueId());
    }

    public void onPlayerJoin(Player player) {
        UUID playerId = player.getUniqueId();
        String path = "players." + playerId;
        if (tempChancesConfig.contains(path)) {
            long expiryTime = tempChancesConfig.getLong(path + ".expiry");
            int chance = tempChancesConfig.getInt(path + ".chance");
            if (expiryTime > System.currentTimeMillis()) startBossBarForPlayer(player, chance, expiryTime);
        }
    }

    public void cleanup() {
        if (updateTask != null && !updateTask.isCancelled()) updateTask.cancel();
        for (UUID playerId : new HashSet<>(activeBossBars.keySet())) removeBossBar(playerId);
    }
}
