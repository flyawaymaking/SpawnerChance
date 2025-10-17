package com.flyaway.spawnerchance;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class SpawnerDropListener implements Listener {

    private final SpawnerChance plugin;
    private final Random random = new Random();

    public SpawnerDropListener(SpawnerChance plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.SPAWNER) {
            CreatureSpawner spawner = (CreatureSpawner) block.getState();
            int chance = getDropChance(player);

            if (chance > 0) {
                double roll = random.nextDouble() * 100;
                if (roll < chance) {
                    ItemStack spawnerItem = createSpawnerItem(spawner);
                    block.getWorld().dropItemNaturally(block.getLocation(), spawnerItem);

                    // Paper: Отменяем стандартный дроп спавнера
                    event.setDropItems(false);

                    String mobName = getMobDisplayName(spawner.getSpawnedType());
                    player.sendMessage("§aУдача! Спавнер " + mobName + " выпал (" + chance + "%)");
                } else {
                    player.sendMessage("§cСпавнер не выпал (" + chance + "%)");
                }
            }
        }
    }

    private ItemStack createSpawnerItem(CreatureSpawner spawner) {
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER, 1);

        BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
        if (meta != null) {
            CreatureSpawner spawnerMeta = (CreatureSpawner) meta.getBlockState();

            // Копируем все данные из оригинального спавнера
            spawnerMeta.setSpawnedType(spawner.getSpawnedType());
            spawnerMeta.setDelay(spawner.getDelay());
            spawnerMeta.setMinSpawnDelay(spawner.getMinSpawnDelay());
            spawnerMeta.setMaxSpawnDelay(spawner.getMaxSpawnDelay());
            spawnerMeta.setSpawnCount(spawner.getSpawnCount());
            spawnerMeta.setMaxNearbyEntities(spawner.getMaxNearbyEntities());
            spawnerMeta.setRequiredPlayerRange(spawner.getRequiredPlayerRange());
            spawnerMeta.setSpawnRange(spawner.getSpawnRange());

            meta.setBlockState(spawnerMeta);

            // Устанавливаем только название без лишнего описания
            String mobName = getMobDisplayName(spawner.getSpawnedType());
            meta.setDisplayName("§6Спавнер " + mobName);
            meta.setLore(new java.util.ArrayList<>());

            spawnerItem.setItemMeta(meta);
        }

        return spawnerItem;
    }

    private String getMobDisplayName(org.bukkit.entity.EntityType entityType) {
        if (entityType == null) {
            return "§7Пустой";
        }

        // Русские названия для популярных мобов
        switch (entityType) {
            case BLAZE: return "Ифрит";
            case PIGLIN: return "Пиглин";
            case COW: return "Корова";
            case WOLF: return "Волк";
            case SHEEP: return "Овца";
            case SHULKER: return "Шалкер";
            case SPIDER: return "Паук";
            case ENDERMAN: return "Эндермен";
            case SLIME: return "Слизь";
            case ZOMBIE: return "Зомби";
            case SKELETON: return "Скелет";
            case CREEPER: return "Крипер";
            case DROWNED: return "Утопленник";
            case MOOSHROOM: return "Муушрум";
            case CHICKEN: return "Курица";
            case GHAST: return "Гаст";
            case MAGMA_CUBE: return "Магмовый куб";
            case PHANTOM: return "Фантом";
            case POLAR_BEAR: return "Белый медведь";
            case BOGGED: return "Болотник";
            case GUARDIAN: return "Страж";
            case BREEZE: return "Вихрь";
            case RABBIT: return "Кролик";
            case HORSE: return "Лошадь";
            case ARMADILLO: return "Броненосец";
            case TURTLE: return "Черепаха";
            case PIG: return "Свинья";
            default:
                // Для остальных мобов используем автоматическое преобразование
                String name = entityType.name().toLowerCase();
                String[] words = name.split("_");
                StringBuilder displayName = new StringBuilder();

                for (String word : words) {
                    if (!word.isEmpty()) {
                        displayName.append(Character.toUpperCase(word.charAt(0)))
                                  .append(word.substring(1))
                                  .append(" ");
                    }
                }

                return displayName.toString().trim();
        }
    }

    private int getDropChance(Player player) {
        Set<String> permissions = player.getEffectivePermissions().stream()
                .map(perm -> perm.getPermission().toLowerCase())
                .collect(Collectors.toSet());

        int maxChance = 0;
        for (String perm : permissions) {
            if (perm.startsWith("spawner.dropchance.")) {
                try {
                    int value = Integer.parseInt(perm.replace("spawner.dropchance.", ""));
                    if (value > maxChance) {
                        maxChance = value;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxChance;
    }
}
