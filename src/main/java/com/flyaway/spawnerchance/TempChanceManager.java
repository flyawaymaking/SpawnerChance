package com.flyaway.spawnerchance;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TempChanceManager {

    private final SpawnerChance plugin;
    private final File tempChancesFile;
    private FileConfiguration tempChancesConfig;
    private final Map<UUID, PermissionAttachment> tempAttachments = new HashMap<>();

    public TempChanceManager(SpawnerChance plugin) {
        this.plugin = plugin;
        this.tempChancesFile = new File(plugin.getDataFolder(), "tempchances.yml");
        loadTempChances();
    }

    public void loadTempChances() {
        if (!tempChancesFile.exists()) {
            try {
                // Создаем папку плагина если её нет
                plugin.getDataFolder().mkdirs();
                // Создаем пустой файл
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
        long duration = plugin.getConfigManager().getTempChanceDuration();
        long expiryTime = System.currentTimeMillis() + duration * 60 * 1000;

        // Проверяем текущий шанс игрока
        int currentChance = getCurrentChance(player);
        if (currentChance >= chance) {
            return false; // У игрока уже есть такой или больший шанс
        }

        // Сохраняем в конфиг
        String path = "players." + playerId;
        tempChancesConfig.set(path + ".name", playerName);
        tempChancesConfig.set(path + ".chance", chance);
        tempChancesConfig.set(path + ".expiry", expiryTime);
        tempChancesConfig.set(path + ".given_by", executor);
        tempChancesConfig.set(path + ".given_at", System.currentTimeMillis());

        saveTempChances();

        // Выдаем временное право
        applyTempPermission(player, chance);
        return true;
    }

    private void applyTempPermission(Player player, int chance) {
        // Удаляем старые временные права
        removeTempPermissions(player);

        // Добавляем новые
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("spawner.dropchance." + chance, true);
        tempAttachments.put(player.getUniqueId(), attachment);
    }

    public void removeTempPermissions(Player player) {
        PermissionAttachment attachment = tempAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            player.removeAttachment(attachment);
        }
    }

    public int getCurrentChance(Player player) {
        int maxChance = 0;
        for (var perm : player.getEffectivePermissions()) {
            String permission = perm.getPermission().toLowerCase();
            if (permission.startsWith("spawner.dropchance.")) {
                try {
                    int value = Integer.parseInt(permission.substring("spawner.dropchance.".length()));
                    if (value > maxChance) {
                        maxChance = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return maxChance;
    }

    public int cleanupExpiredChances() {
        List<String> toRemove = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;

        if (tempChancesConfig.contains("players")) {
            for (String playerId : tempChancesConfig.getConfigurationSection("players").getKeys(false)) {
                long expiry = tempChancesConfig.getLong("players." + playerId + ".expiry");
                if (expiry < currentTime) {
                    toRemove.add(playerId);
                    removedCount++;

                    // Убираем права у онлайн игроков
                    UUID uuid = UUID.fromString(playerId);
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null) {
                        removeTempPermissions(player);
                    }
                }
            }

            // Удаляем истекшие записи
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
}
