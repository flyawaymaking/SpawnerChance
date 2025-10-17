package com.flyaway.spawnerchance;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SpawnerRestrictionListener implements Listener {

    private final SpawnerChance plugin;

    public SpawnerRestrictionListener(SpawnerChance plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Проверяем, что это правое нажатие по блоку
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Проверяем, что блок - это спавнер и в руке яйцо призыва
        if (block == null || block.getType() != Material.SPAWNER ||
            !itemInHand.getType().name().endsWith("_SPAWN_EGG")) return;

        // Если у игрока есть право обхода ограничений - пропускаем проверку
        if (player.hasPermission("spawner.bypass")) {
            player.sendMessage(Component.text("Вы обошли ограничения спавнера!", NamedTextColor.GOLD));
            return;
        }

        // Получаем тип моба из яйца
        EntityType entityType = getEntityTypeFromSpawnEgg(itemInHand);
        if (entityType == null) return;

        // Проверяем, разрешен ли этот моб для спавнеров
        if (!plugin.getConfigManager().isMobAllowedInSpawner(entityType)) {
            event.setCancelled(true);
            Component mobName = getMobDisplayName(entityType);
            Component message = Component.text("Ошибка: Моб ", NamedTextColor.RED)
                    .append(mobName)
                    .append(Component.text(" нельзя поместить в спавнер!", NamedTextColor.RED));
            player.sendMessage(message);
            return;
        }

        // Если моб разрешен, позволяем стандартное поведение
        Component mobName = getMobDisplayName(entityType);
        Component message = Component.text("Вы установили спавнер для: ", NamedTextColor.GREEN)
                .append(mobName);
        player.sendMessage(message);
    }

    private EntityType getEntityTypeFromSpawnEgg(ItemStack item) {
        String itemName = item.getType().name().toUpperCase();

        if (itemName.endsWith("_SPAWN_EGG")) {
            String mobName = itemName.replace("_SPAWN_EGG", "");
            try {
                return EntityType.valueOf(mobName);
            } catch (IllegalArgumentException e) {
                // Paper: Логируем неизвестные яйца призыва
                plugin.getLogger().warning("Неизвестный тип яйца призыва: " + mobName);
                return null;
            }
        }
        return null;
    }

    private Component getMobDisplayName(EntityType entityType) {
        if (entityType == null) {
            return Component.text("Неизвестный", NamedTextColor.GRAY);
        }

        // Русские названия для популярных мобов
        String displayName;
        switch (entityType) {
            case BLAZE: displayName = "Ифрит"; break;
            case PIGLIN: displayName = "Пиглин"; break;
            case COW: displayName = "Корова"; break;
            case WOLF: displayName = "Волк"; break;
            case SHEEP: displayName = "Овца"; break;
            case SHULKER: displayName = "Шалкер"; break;
            case SPIDER: displayName = "Паук"; break;
            case ENDERMAN: displayName = "Эндермен"; break;
            case SLIME: displayName = "Слизь"; break;
            case ZOMBIE: displayName = "Зомби"; break;
            case SKELETON: displayName = "Скелет"; break;
            case CREEPER: displayName = "Крипер"; break;
            case DROWNED: displayName = "Утопленник"; break;
            case MOOSHROOM: displayName = "Муушрум"; break;
            case CHICKEN: displayName = "Курица"; break;
            case GHAST: displayName = "Гаст"; break;
            case MAGMA_CUBE: displayName = "Магмовый куб"; break;
            case PHANTOM: displayName = "Фантом"; break;
            case POLAR_BEAR: displayName = "Белый медведь"; break;
            case BOGGED: displayName = "Болотник"; break;
            case GUARDIAN: displayName = "Страж"; break;
            case BREEZE: displayName = "Бриз"; break;
            case RABBIT: displayName = "Кролик"; break;
            case HORSE: displayName = "Лошадь"; break;
            case ARMADILLO: displayName = "Броненосец"; break;
            case TURTLE: displayName = "Черепаха"; break;
            case PIG: displayName = "Свинья"; break;
            default:
                // Для остальных мобов используем автоматическое преобразование
                String name = entityType.name().toLowerCase();
                String[] words = name.split("_");
                StringBuilder nameBuilder = new StringBuilder();

                for (String word : words) {
                    if (!word.isEmpty()) {
                        nameBuilder.append(Character.toUpperCase(word.charAt(0)))
                                  .append(word.substring(1))
                                  .append(" ");
                    }
                }
                displayName = nameBuilder.toString().trim();
                break;
        }

        return Component.text(displayName, NamedTextColor.YELLOW);
    }
}
