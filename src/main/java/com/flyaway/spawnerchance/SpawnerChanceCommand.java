package com.flyaway.spawnerchance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpawnerChanceCommand implements CommandExecutor, TabExecutor {
    private final SpawnerChance plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final TempChanceManager tempChanceManager;
    private final MiniMessage miniMessage;

    public SpawnerChanceCommand(SpawnerChance plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.tempChanceManager = plugin.getTempChanceManager();
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spawner.command.use") && !(sender instanceof ConsoleCommandSender)) {
            String noPermission = configManager.getMessage("no-permission");
            plugin.sendMessage(sender, miniMessage.deserialize(noPermission));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                reloadCommand(sender);
                break;
            case "info":
                infoCommand(sender);
                break;
            case "tempchance":
                tempChanceCommand(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void reloadCommand(CommandSender sender) {
        configManager.reloadConfig();
        languageManager.load();
        tempChanceManager.cleanupExpiredChances();

        String reloadSuccess = configManager.getMessage("reload-success");
        plugin.sendMessage(sender, miniMessage.deserialize(reloadSuccess));
    }

    private void infoCommand(CommandSender sender) {
        long durationMinutes = configManager.getTempChanceDuration();
        long hours = durationMinutes / 60;
        long minutes = durationMinutes % 60;

        String durationMessage = configManager.getMessage("info-duration")
                .replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", String.valueOf(minutes));

        Component durationInfo = miniMessage.deserialize(durationMessage);

        Set<EntityType> allowedMobs = configManager.getAllowedSpawnerMobs();
        Component mobsInfo;

        if (allowedMobs.isEmpty()) {
            String allMobsMessage = configManager.getMessage("info-mobs-all");
            mobsInfo = miniMessage.deserialize(allMobsMessage);
        } else {
            String mobsList = allowedMobs.stream()
                    .map(mobType -> {
                        String translated = languageManager.translate(mobType);
                        return "<white>" + translated + "</white>";
                    })
                    .collect(Collectors.joining(", "));

            String mobsMessage = configManager.getMessage("info-mobs-list")
                    .replace("{mobs}", mobsList);
            mobsInfo = miniMessage.deserialize(mobsMessage);
        }

        plugin.sendMessage(sender, durationInfo);
        plugin.sendMessage(sender, mobsInfo);
    }

    private void tempChanceCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            String usage = configManager.getMessage("tempchance-usage");
            plugin.sendMessage(sender, miniMessage.deserialize(usage));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            String playerNotFound = configManager.getMessage("player-not-found");
            plugin.sendMessage(sender, miniMessage.deserialize(playerNotFound));
            return;
        }

        int chance;
        try {
            chance = Integer.parseInt(args[2]);
            if (chance < 1 || chance > 100) {
                String invalidChance = configManager.getMessage("invalid-chance-range");
                plugin.sendMessage(sender, miniMessage.deserialize(invalidChance));
                return;
            }
        } catch (NumberFormatException e) {
            String invalidNumber = configManager.getMessage("invalid-number");
            plugin.sendMessage(sender, miniMessage.deserialize(invalidNumber));
            return;
        }

        String executorName = sender instanceof Player ? sender.getName() : "Console";
        boolean success = tempChanceManager.addTempChance(target, chance, executorName);

        if (success) {
            long durationMinutes = configManager.getTempChanceDuration();
            long hours = durationMinutes / 60;
            long minutes = durationMinutes % 60;

            String durationText = (hours > 0 ? hours + "ч " : "") + minutes + "м";

            String senderMessage = configManager.getMessage("tempchance-success-sender")
                    .replace("{player}", target.getName())
                    .replace("{chance}", String.valueOf(chance))
                    .replace("{duration}", durationText);
            plugin.sendMessage(sender, miniMessage.deserialize(senderMessage));

            String targetMessage = configManager.getMessage("tempchance-success-target")
                    .replace("{chance}", String.valueOf(chance))
                    .replace("{duration}", durationText);
            plugin.sendMessage(target, miniMessage.deserialize(targetMessage));
        } else {
            int currentChance = plugin.getDropChance(target);
            String alreadyHasChance = configManager.getMessage("player-already-has-chance")
                    .replace("{player}", target.getName())
                    .replace("{chance}", String.valueOf(currentChance));
            plugin.sendMessage(sender, miniMessage.deserialize(alreadyHasChance));
        }
    }

    private void sendHelp(CommandSender sender) {
        String helpMessage = configManager.getMessage("help");
        plugin.sendMessage(sender, miniMessage.deserialize(helpMessage));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("spawner.command.use") && !(sender instanceof ConsoleCommandSender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Stream.of("reload", "info", "tempchance")
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("tempchance")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("tempchance")) {
            return Stream.of("10", "25", "50", "75", "100")
                    .filter(num -> num.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
