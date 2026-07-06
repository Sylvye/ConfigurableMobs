package com.bountysmp.configurablemobs.command;

import com.bountysmp.configurablemobs.ConfigurableMobsPlugin;
import com.bountysmp.configurablemobs.data.CustomMobManager;
import com.bountysmp.configurablemobs.data.SpawnRuleManager;
import com.bountysmp.configurablemobs.gui.GuiManager;
import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import com.bountysmp.configurablemobs.trigger.TriggerManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class ConfigurableMobsCommand implements CommandExecutor, TabCompleter {
    private final ConfigurableMobsPlugin plugin;
    private final CustomMobManager customMobManager;
    private final SpawnRuleManager spawnRuleManager;
    private final GuiManager guiManager;
    private final TriggerManager triggerManager;

    public ConfigurableMobsCommand(
            ConfigurableMobsPlugin plugin,
            CustomMobManager customMobManager,
            SpawnRuleManager spawnRuleManager,
            GuiManager guiManager,
            TriggerManager triggerManager) {
        this.plugin = plugin;
        this.customMobManager = customMobManager;
        this.spawnRuleManager = spawnRuleManager;
        this.guiManager = guiManager;
        this.triggerManager = triggerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("configurablemobs.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use ConfigurableMobs.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the GUI.");
                return true;
            }
            guiManager.openMain(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAllData();
            sender.sendMessage("ConfigurableMobs data reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("summon")) {
            return summon(sender, args);
        }

        sender.sendMessage("Usage: /" + label + " [summon <mobId> [<x> <y> <z>]|summon <mobId> <world> <x> <y> <z>|reload]");
        return true;
    }

    private boolean summon(CommandSender sender, String[] args) {
        if (args.length != 2 && args.length != 5 && args.length != 6) {
            sendSummonUsage(sender);
            return true;
        }
        CustomMobDefinition definition = customMobManager.get(args[1]).orElse(null);
        if (definition == null) {
            sender.sendMessage("Unknown custom mob: " + args[1]);
            return true;
        }

        try {
            Location location = summonLocation(sender, args);
            Entity entity = definition.spawn(location, plugin.customMobKey());
            triggerManager.fireSpawn(entity);
            sender.sendMessage("Summoned " + definition.id() + ".");
        } catch (CoordinateException exception) {
            sender.sendMessage(exception.getMessage());
        }
        return true;
    }

    private Location summonLocation(CommandSender sender, String[] args) throws CoordinateException {
        Location executorLocation = executorLocation(sender);
        if (args.length == 2) {
            if (executorLocation == null) {
                throw new CoordinateException("Only players, entities, and command blocks can use /cm summon <mobId>.");
            }
            return executorLocation;
        }
        if (args.length == 5) {
            if (executorLocation == null) {
                throw new CoordinateException("Only players, entities, and command blocks can omit the world.");
            }
            return parseLocation(executorLocation.getWorld(), executorLocation, args[2], args[3], args[4]);
        }

        World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            throw new CoordinateException("Unknown world: " + args[2]);
        }
        return parseLocation(world, executorLocation, args[3], args[4], args[5]);
    }

    private Location parseLocation(World world, Location origin, String xInput, String yInput, String zInput) throws CoordinateException {
        return new Location(
                world,
                parseCoordinate(xInput, origin == null ? null : origin.getX()),
                parseCoordinate(yInput, origin == null ? null : origin.getY()),
                parseCoordinate(zInput, origin == null ? null : origin.getZ()));
    }

    private double parseCoordinate(String input, Double origin) throws CoordinateException {
        if (input.startsWith("~")) {
            if (origin == null) {
                throw new CoordinateException("Relative coordinates require an executor position.");
            }
            String offset = input.substring(1);
            if (offset.isBlank()) {
                return origin;
            }
            try {
                return origin + Double.parseDouble(offset);
            } catch (NumberFormatException exception) {
                throw new CoordinateException("Coordinates must be numbers or relative values like ~, ~1, or ~-2.5.");
            }
        }
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            throw new CoordinateException("Coordinates must be numbers or relative values like ~, ~1, or ~-2.5.");
        }
    }

    private Location executorLocation(CommandSender sender) {
        if (sender instanceof Entity entity) {
            return entity.getLocation();
        }
        if (sender instanceof BlockCommandSender blockCommandSender) {
            return blockCommandSender.getBlock().getLocation().add(0.5, 0.0, 0.5);
        }
        return null;
    }

    private void sendSummonUsage(CommandSender sender) {
        sender.sendMessage("Usage: /cm summon <mobId>");
        sender.sendMessage("Usage: /cm summon <mobId> <x> <y> <z>");
        sender.sendMessage("Usage: /cm summon <mobId> <world> <x> <y> <z>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("configurablemobs.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("summon", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("summon")) {
            return filter(customMobManager.all().stream().map(CustomMobDefinition::id).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("summon")) {
            List<String> suggestions = new ArrayList<>(coordinateSuggestions(sender, 0));
            suggestions.addAll(Bukkit.getWorlds().stream().map(World::getName).toList());
            return filter(suggestions, args[2]);
        }
        if (args.length >= 4 && args.length <= 6 && args[0].equalsIgnoreCase("summon")) {
            World explicitWorld = Bukkit.getWorld(args[2]);
            int axis = switch (args.length) {
                case 4 -> explicitWorld == null ? 1 : 0;
                case 5 -> explicitWorld == null ? 2 : 1;
                default -> 2;
            };
            return filter(coordinateSuggestions(sender, axis), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> coordinateSuggestions(CommandSender sender, int axis) {
        Location location = executorLocation(sender);
        if (location == null) {
            return List.of();
        }
        String coordinate = switch (axis) {
            case 0 -> String.valueOf(location.getBlockX());
            case 1 -> String.valueOf(location.getBlockY());
            default -> String.valueOf(location.getBlockZ());
        };
        return List.of(coordinate, "~");
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }

    private static final class CoordinateException extends Exception {
        private CoordinateException(String message) {
            super(message);
        }
    }
}
