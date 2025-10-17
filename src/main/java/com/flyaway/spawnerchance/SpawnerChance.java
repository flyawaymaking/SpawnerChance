package com.flyaway.spawnerchance;

import org.bukkit.plugin.java.JavaPlugin;

public class SpawnerChance extends JavaPlugin {

    private ConfigManager configManager;
    private static SpawnerChance instance;

    @Override
    public void onEnable() {
        instance = this;

        // Сохраняем дефолтный конфиг если его нет
        saveDefaultConfig();

        // Инициализация конфиг-менеджера
        this.configManager = new ConfigManager(this);

        // Регистрация слушателей
        getServer().getPluginManager().registerEvents(new SpawnerDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerRestrictionListener(this), this);

        getLogger().info("SpawnerChance включён!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SpawnerChance выключен!");
    }

    public static SpawnerChance getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
