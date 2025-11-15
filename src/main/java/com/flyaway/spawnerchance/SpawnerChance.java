package com.flyaway.spawnerchance;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SpawnerChance extends JavaPlugin {

    private static SpawnerChance instance;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private TempChanceManager tempChanceManager;
    private BukkitRunnable cleanupTask;

    @Override
    public void onEnable() {
        instance = this;

        // Сохраняем дефолтный конфиг если его нет
        saveDefaultConfig();

        // Инициализация менеджеров
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        languageManager.load();
        this.tempChanceManager = new TempChanceManager(this);
        startCleanupTask();

        // Регистрация слушателей
        getServer().getPluginManager().registerEvents(new SpawnerDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerRestrictionListener(this), this);

        // Регистрация команд
        getCommand("spawnerchance").setExecutor(new SpawnerChanceCommand(this));
        getCommand("spawnerchance").setTabCompleter(new SpawnerChanceCommand(this));

        getLogger().info("SpawnerChance включён!");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        // Сохраняем временные права при выключении
        if (tempChanceManager != null) {
            tempChanceManager.saveTempChances();
        }

        getLogger().info("SpawnerChance выключен!");
    }

    private void startCleanupTask() {
        // Получаем интервал из конфига (в минутах, по умолчанию 5 минут)
        long cleanupInterval = configManager.getCleanupInterval() * 20; // Конвертируем в тики

        this.cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                int cleaned = tempChanceManager.cleanupExpiredChances();
                if (cleaned > 0) {
                    getLogger().info("Периодическая очистка: удалено " + cleaned + " истекших временных прав");
                }
            }
        };
        cleanupTask.runTaskTimer(this, cleanupInterval, cleanupInterval);

        getLogger().info("Задача очистки истекших прав запущена с интервалом " + configManager.getCleanupInterval() + " секунд");
    }

    public static SpawnerChance getInstance() {
        return instance;
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
}
