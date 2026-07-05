package com.bountysmp.configurablemobs.spawn;

import com.bountysmp.configurablemobs.data.SpawnRuleManager;
import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import com.bountysmp.configurablemobs.model.WorldMobRule;
import com.bountysmp.configurablemobs.trigger.TriggerManager;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class NaturalSpawnListener implements Listener {
    private final SpawnRuleManager spawnRuleManager;
    private final TriggerManager triggerManager;
    private final NamespacedKey customMobKey;

    public NaturalSpawnListener(SpawnRuleManager spawnRuleManager, TriggerManager triggerManager, NamespacedKey customMobKey) {
        this.spawnRuleManager = spawnRuleManager;
        this.triggerManager = triggerManager;
        this.customMobKey = customMobKey;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }

        Optional<WorldMobRule> rule = spawnRuleManager.get(event.getLocation().getWorld(), event.getEntityType());
        if (rule.isPresent() && !rule.get().vanillaEnabled()) {
            event.setCancelled(true);
            return;
        }

        Optional<CustomMobDefinition> replacement = spawnRuleManager.chooseReplacement(event.getLocation().getWorld(), event.getEntityType());
        if (replacement.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        Entity entity = replacement.get().spawn(event.getLocation(), customMobKey);
        triggerManager.fireSpawn(entity);
    }
}
