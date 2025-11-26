package com.flyaway.spawnerchance;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SpawnerRestrictionListener implements Listener {

    private final SpawnerChance plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerRestrictionListener(SpawnerChance plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (block == null || block.getType() != Material.SPAWNER ||
                !itemInHand.getType().name().endsWith("_SPAWN_EGG")) return;

        if (player.hasPermission("spawner.bypass")) {
            plugin.sendMessage(player, miniMessage.deserialize(configManager.getMessage("bypass")));
            return;
        }

        EntityType entityType = getEntityTypeFromSpawnEgg(itemInHand);
        if (entityType == null) return;

        String mobName = getMobDisplayName(entityType);

        if (!plugin.getConfigManager().isMobAllowedInSpawner(entityType)) {
            event.setCancelled(true);

            plugin.sendMessage(player, miniMessage.deserialize(configManager.getMessage("egg-set-error").replace("{mob}", mobName)));
            return;
        }

        plugin.sendMessage(player, miniMessage.deserialize(configManager.getMessage("egg-set-success").replace("{mob}", mobName)));
    }

    private EntityType getEntityTypeFromSpawnEgg(ItemStack item) {
        String itemName = item.getType().name().toUpperCase();

        if (itemName.endsWith("_SPAWN_EGG")) {
            String mobName = itemName.replace("_SPAWN_EGG", "");
            try {
                return EntityType.valueOf(mobName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный тип яйца призыва: " + mobName);
                return null;
            }
        }
        return null;
    }

    private String getMobDisplayName(EntityType entityType) {
        if (entityType == null) {
            return configManager.getMessage("unknown-mob");
        }

        return languageManager.translate(entityType);
    }
}
