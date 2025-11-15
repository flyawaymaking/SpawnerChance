package com.flyaway.spawnerchance;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
    private ConfigManager configManager;
    private LuckPerms luckPerms;
    private BukkitRunnable updateTask;

    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();

    public TempChanceManager(SpawnerChance plugin) {
        this.plugin = plugin;
        this.tempChancesFile = new File(plugin.getDataFolder(), "tempchances.yml");
        this.configManager = plugin.getConfigManager();
        initLuckPerms();
        loadTempChances();
        startBossBarUpdateTask();
    }

    // Запускаем периодическую задачу для обновления BossBar
    private void startBossBarUpdateTask() {
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllBossBars();
            }
        };
        updateTask.runTaskTimer(plugin, 0L, 20L); // Обновляем каждую секунду
    }

    private void initLuckPerms() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            RegisteredServiceProvider<LuckPerms> provider =
                    plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPerms = provider.getProvider();
                plugin.getLogger().info("LuckPerms обнаружен, используется API.");
            } else {
                plugin.getLogger().warning("LuckPerms установлен, но провайдер не найден!");
            }
        } else {
            plugin.getLogger().warning("LuckPerms не обнаружен! Временные права работать не будут.");
        }
    }

    public void loadTempChances() {
        if (!tempChancesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                tempChancesFile.createNewFile();
                plugin.getLogger().info("Создан новый файл tempchances.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать tempchances.yml: " + e.getMessage());
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
            plugin.getLogger().warning("Не удалось сохранить tempchances.yml: " + e.getMessage());
        }
    }

    public boolean addTempChance(Player player, int chance, String executor) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        long duration = configManager.getTempChanceDuration();
        long expiryTime = System.currentTimeMillis() + duration * 60 * 1000;

        // Проверяем текущий шанс игрока
        int currentChance = plugin.getDropChance(player);
        if (currentChance > chance) {
            return false; // У игрока уже есть больший шанс
        }
        String path = "players." + playerId;

        if (tempChancesConfig.contains(path)) {
            removeTempPermission(playerId, tempChancesConfig.getInt(path + ".chance", 0));
        }

        tempChancesConfig.set(path + ".name", playerName);
        tempChancesConfig.set(path + ".chance", chance);
        tempChancesConfig.set(path + ".expiry", expiryTime);
        tempChancesConfig.set(path + ".given_by", executor);
        tempChancesConfig.set(path + ".given_at", System.currentTimeMillis());

        saveTempChances();

        boolean success = applyTempPermission(playerId, chance);
        if (success) {
            startBossBarForPlayer(player, chance, expiryTime);
        }
        return success;
    }

    private boolean applyTempPermission(UUID playerId, int chance) {
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms не доступен, невозможно выдать временное право");
            return false;
        }

        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(playerId);
        try {
            User user = userFuture.join();
            if (user == null) {
                plugin.getLogger().warning("Не удалось загрузить пользователя: " + playerId);
                return false;
            }

            // Добавляем новое право
            String permission = "spawner.dropchance." + chance;
            Node node = Node.builder(permission).value(true).build();
            user.data().add(node);

            // Сохраняем изменения
            luckPerms.getUserManager().saveUser(user);
            plugin.getLogger().info("Выдано временное право " + permission + " для " + playerId);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при выдаче временного права: " + e.getMessage());
            return false;
        }
    }

    public boolean removeTempPermission(UUID playerId, int chance) {
        if (luckPerms == null) {
            return false;
        }

        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(playerId);
        try {
            User user = userFuture.join();
            if (user == null) return false;

            String permission = "spawner.dropchance." + chance;

            // Ищем и удаляем конкретное право
            for (Node node : user.getNodes()) {
                if (node.getKey().equals(permission)) {
                    user.data().remove(node);
                    luckPerms.getUserManager().saveUser(user);
                    plugin.getLogger().info("Удалено временное право " + permission + " для " + playerId);
                    break;
                }
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при удалении временного права: " + e.getMessage());
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

                        // Удаляем право через LuckPerms
                        boolean rightsRemoved = removeTempPermission(uuid, chance);

                        if (rightsRemoved) {
                            toRemove.add(playerId);
                            removedCount++;
                            removeBossBar(uuid);
                        }
                    }
                }
            }

            // Удаляем истекшие записи из конфига
            for (String playerId : toRemove) {
                tempChancesConfig.set("players." + playerId, null);
            }

            if (removedCount > 0) {
                saveTempChances();
                plugin.getLogger().info("Автоматически удалено " + removedCount + " истекших временных прав");
            }
        }

        return removedCount;
    }

    // BossBar методы
    private void startBossBarForPlayer(Player player, int chance, long expiryTime) {
        UUID playerId = player.getUniqueId();

        removeBossBar(playerId);

        String initialTitle = configManager.getMessage("bossbar-initial-title").replace("{chance}", String.valueOf(chance));
        BossBar bossBar = Bukkit.createBossBar(initialTitle, BarColor.GREEN, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBar.setVisible(true);

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
        bossBar.setProgress(progress);

        String timeText;
        BarColor color;

        if (timeLeft > 60000) { // Больше 1 минуты - показываем минуты
            long minutesLeft = timeLeft / 60000;
            timeText = configManager.getMessage("bossbar-minutes-left")
                    .replace("{chance}", String.valueOf(chance))
                    .replace("{minutes}", String.valueOf(minutesLeft));
            color = minutesLeft > 5 ? BarColor.GREEN : BarColor.YELLOW;
        } else { // Меньше 1 минуты - показываем секунды
            long secondsLeft = timeLeft / 1000;
            timeText = configManager.getMessage("bossbar-seconds-left")
                    .replace("{chance}", String.valueOf(chance))
                    .replace("{seconds}", String.valueOf(secondsLeft));
            color = BarColor.RED;
        }

        bossBar.setTitle(timeText);
        bossBar.setColor(color);

        // Если время вышло, удаляем BossBar
        if (timeLeft <= 0) {
            removeBossBar(playerId);
        }
    }

    private void removeBossBar(UUID playerId) {
        BossBar bossBar = activeBossBars.remove(playerId);
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
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

            if (expiryTime > System.currentTimeMillis()) {
                startBossBarForPlayer(player, chance, expiryTime);
            }
        }
    }

    public void cleanup() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        for (UUID playerId : new HashSet<>(activeBossBars.keySet())) {
            removeBossBar(playerId);
        }
    }
}
