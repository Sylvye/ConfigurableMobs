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
import org.bukkit.Location;
import org.bukkit.World;
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
            sender.sendMessage("You do not have permission to use ConfigurableMobs.");
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

        sender.sendMessage("Usage: /" + label + " [summon <mobId> <world> <x> <y> <z>|reload]");
        return true;
    }

    private boolean summon(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage("Usage: /cmobs summon <mobId> <world> <x> <y> <z>");
            return true;
        }
        CustomMobDefinition definition = customMobManager.get(args[1]).orElse(null);
        if (definition == null) {
            sender.sendMessage("Unknown custom mob: " + args[1]);
            return true;
        }
        World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            sender.sendMessage("Unknown world: " + args[2]);
            return true;
        }
        try {
            Location location = new Location(world, Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
            Entity entity = definition.spawn(location, plugin.customMobKey());
            triggerManager.fireSpawn(entity);
            sender.sendMessage("Summoned " + definition.id() + ".");
        } catch (NumberFormatException exception) {
            sender.sendMessage("Coordinates must be numbers.");
        }
        return true;
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
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[2]);
        }
        return List.of();
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
}
