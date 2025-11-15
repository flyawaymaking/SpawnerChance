package com.flyaway.spawnerchance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SpawnerDropListener implements Listener {
    private final SpawnerChance plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Random random = new Random();

    public SpawnerDropListener(SpawnerChance plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled() || event.getBlock().getType() != Material.SPAWNER) return;
        if (event.getExpToDrop() == 0) return; // Already processed

        Player player = event.getPlayer();
        int chance = plugin.getDropChance(player);
        if (chance <= 0) return;

        double roll = random.nextDouble() * 100;
        if (roll < chance) {
            handleSuccessfulDrop(event, player, chance);
        } else {
            handleFailedDrop(player, chance);
        }
    }

    private void handleSuccessfulDrop(BlockBreakEvent event, Player player, int chance) {
        event.setDropItems(false);
        event.setExpToDrop(0);

        Locale locale = player.locale();
        CreatureSpawner spawner = (CreatureSpawner) event.getBlock().getState();

        String mobName = getMobDisplayName(spawner.getSpawnedType(), locale);
        String spawnerName = getTranslationForKey("block.minecraft.spawner", locale);
        String spawnerRawName = configManager.getMessage("mob-spawner").replace("{spawner}", spawnerName).replace("{mob}", mobName);

        ItemStack spawnerItem = createSpawnerItem(spawner, spawnerRawName);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), spawnerItem);

        sendMessage(player, "drop-success", spawnerRawName, chance);
    }

    private void handleFailedDrop(Player player, int chance) {
        sendMessage(player, "drop-failure", "", chance);
    }

    private void sendMessage(Player player, String messageKey, String spawnerName, int chance) {
        String message = configManager.getMessage(messageKey);
        if (message.isEmpty()) return;

        String formatted = message.replace("{mob-spawner}", spawnerName).replace("{chance}", String.valueOf(chance));

        plugin.sendMessage(player, miniMessage.deserialize(formatted));
    }

    private ItemStack createSpawnerItem(@NotNull CreatureSpawner spawner, @NotNull String spawnerName) {
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER);

        BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
        if (meta == null) return spawnerItem;

        CreatureSpawner target = (CreatureSpawner) meta.getBlockState();

        target.setSpawnedType(spawner.getSpawnedType());

        meta.setBlockState(target);

        if (spawner.getSpawnedType() != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "spawner-type"),
                    PersistentDataType.STRING,
                    spawner.getSpawnedType().name()
            );
        }

        meta.displayName(miniMessage.deserialize(spawnerName));
        meta.lore(List.of());

        spawnerItem.setItemMeta(meta);
        return spawnerItem;
    }

    private String getMobDisplayName(EntityType entityType, @NotNull Locale locale) {
        if (entityType == null) {
            return configManager.getMessage("empty-spawner-name");
        }
        return getTranslationForKey(entityType.translationKey(), locale);
    }

    private String getTranslationForKey(@NotNull String key, @NotNull Locale locale) {
        return languageManager.translateToString(Component.translatable(key), locale);
    }
}
