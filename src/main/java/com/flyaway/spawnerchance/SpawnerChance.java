package com.flyaway.spawnerchance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SpawnerChance extends JavaPlugin {
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private TempChanceManager tempChanceManager;
    private BukkitRunnable cleanupTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.tempChanceManager = new TempChanceManager(this);
        startCleanupTask();

        getServer().getPluginManager().registerEvents(new SpawnerDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerRestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                tempChanceManager.onPlayerJoin(event.getPlayer());
            }

            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                tempChanceManager.onPlayerQuit(event.getPlayer());
            }
        }, this);

        getCommand("spawnerchance").setExecutor(new SpawnerChanceCommand(this));
        getCommand("spawnerchance").setTabCompleter(new SpawnerChanceCommand(this));

        hookPlaceholderAPI();
        getLogger().info("SpawnerChance enabled!");
    }

    private void hookPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SpawnerChancePlaceholder(this).register();
            getLogger().info("PlaceholderAPI found, placeholders enabled");
        }
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        if (tempChanceManager != null) {
            tempChanceManager.saveTempChances();
            tempChanceManager.cleanup();
        }

        getLogger().info("SpawnerChance disabled!");
    }

    private void startCleanupTask() {
        long cleanupInterval = configManager.getCleanupInterval() * 20;

        this.cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                int cleaned = tempChanceManager.cleanupExpiredChances();
                if (cleaned > 0) {
                    getLogger().info("Periodic cleaning: removed " + cleaned + " expired temporary permissions");
                }
            }
        };
        cleanupTask.runTaskTimer(this, cleanupInterval, cleanupInterval);

        getLogger().info("The task of clearing expired permissions is started with an interval " + configManager.getCleanupInterval() + " seconds");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public TempChanceManager getTempChanceManager() {
        return tempChanceManager;
    }

    public int getDropChance(Player player) {
        int maxChance = 0;

        for (var perm : player.getEffectivePermissions()) {
            String permission = perm.getPermission().toLowerCase();
            String PREFIX = "spawner.dropchance.";

            if (permission.startsWith(PREFIX)) {
                try {
                    int value = Integer.parseInt(permission.substring(PREFIX.length()));
                    if (value > maxChance) {
                        maxChance = value;
                        if (maxChance >= 100) break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return maxChance;
    }

    public void sendMessage(CommandSender sender, Component message) {
        Component prefix = MiniMessage.miniMessage().deserialize(configManager.getMessage("prefix"));
        sender.sendMessage(prefix.append(message));
    }
}
