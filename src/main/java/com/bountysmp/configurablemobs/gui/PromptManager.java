package com.bountysmp.configurablemobs.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PromptManager implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();

    public PromptManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String message, Consumer<String> consumer) {
        prompts.put(player.getUniqueId(), consumer);
        player.closeInventory();
        player.sendMessage(message);
        player.sendMessage("Type cancel to abort.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> consumer = prompts.remove(event.getPlayer().getUniqueId());
        if (consumer == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        if (message.equalsIgnoreCase("cancel")) {
            event.getPlayer().sendMessage("Cancelled.");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> consumer.accept(message));
    }
}
