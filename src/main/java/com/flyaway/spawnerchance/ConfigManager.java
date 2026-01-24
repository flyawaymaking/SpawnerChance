package com.flyaway.spawnerchance;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private Set<EntityType> allowedSpawnerMobs;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        loadAllowedSpawnerMobs();
    }

    private void loadAllowedSpawnerMobs() {
        allowedSpawnerMobs = new HashSet<>();

        if (!config.contains("allowed-spawner-mobs")) {
            plugin.getLogger().warning("There is no section in the config 'allowed-spawner-mobs'! Default mobs are used.");
            setDefaultMobs();
            return;
        }

        for (String mobName : config.getStringList("allowed-spawner-mobs")) {
            try {
                EntityType entityType = EntityType.valueOf(mobName.toUpperCase());
                allowedSpawnerMobs.add(entityType);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown type of mob: " + mobName);
            }
        }

        plugin.getLogger().info("Loaded " + allowedSpawnerMobs.size() + " allowed mobs for spawners");
    }

    private void setDefaultMobs() {
        allowedSpawnerMobs.add(EntityType.ZOMBIE);
        allowedSpawnerMobs.add(EntityType.SKELETON);
        allowedSpawnerMobs.add(EntityType.SPIDER);
        allowedSpawnerMobs.add(EntityType.CREEPER);
        allowedSpawnerMobs.add(EntityType.CAVE_SPIDER);
        allowedSpawnerMobs.add(EntityType.BLAZE);
        allowedSpawnerMobs.add(EntityType.ENDERMAN);
        allowedSpawnerMobs.add(EntityType.SILVERFISH);
        allowedSpawnerMobs.add(EntityType.WITCH);
        allowedSpawnerMobs.add(EntityType.SLIME);
        allowedSpawnerMobs.add(EntityType.MAGMA_CUBE);

        List<String> defaultMobs = new ArrayList<>();
        for (EntityType type : allowedSpawnerMobs) {
            defaultMobs.add(type.name());
        }
        config.set("allowed-spawner-mobs", defaultMobs);
        plugin.saveConfig();
    }

    public boolean isMobAllowedInSpawner(EntityType entityType) {
        return allowedSpawnerMobs.isEmpty() || allowedSpawnerMobs.contains(entityType);
    }

    public long getTempChanceDuration() {
        return config.getLong("temp-chance-duration", 60L); // В минутах
    }

    public long getCleanupInterval() {
        return config.getLong("cleanup-interval", 5L); // В секундах
    }

    public Set<EntityType> getAllowedSpawnerMobs() {
        return Collections.unmodifiableSet(allowedSpawnerMobs);
    }

    public String getMessage(String key) {
        String message = config.getString("messages." + key, "<red>message." + key + " not-found");
        return message.replaceAll("\\s+$", "");
    }

    public String getLanguage() {
        return config.getString("language", "ru_RU");
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadAllowedSpawnerMobs();
    }
}
