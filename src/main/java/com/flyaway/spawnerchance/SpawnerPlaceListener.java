package com.flyaway.spawnerchance;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

public class SpawnerPlaceListener implements Listener {

    private final SpawnerChance plugin;

    public SpawnerPlaceListener(SpawnerChance plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;

        ItemStack item = event.getItemInHand();
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (meta == null) return;

        String typeKey = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "spawner-type"),
                PersistentDataType.STRING
        );
        if (typeKey == null) return;

        CreatureSpawner spawner = (CreatureSpawner) event.getBlockPlaced().getState();
        try {
            EntityType type = EntityType.valueOf(typeKey);
            spawner.setSpawnedType(type);
            spawner.update(true, false);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
