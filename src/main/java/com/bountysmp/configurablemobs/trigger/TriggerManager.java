package com.bountysmp.configurablemobs.trigger;

import com.bountysmp.configurablemobs.data.CustomMobManager;
import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import com.bountysmp.configurablemobs.model.MobTrigger;
import com.bountysmp.configurablemobs.model.TriggerType;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

public final class TriggerManager implements Listener {
    private static final Pattern RAND_PATTERN = Pattern.compile("RAND\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private final JavaPlugin plugin;
    private final CustomMobManager customMobManager;
    private final NamespacedKey customMobKey;
    private final Set<UUID> damageTriggeredDeaths = new HashSet<>();
    private BukkitTask stepTask;

    public TriggerManager(JavaPlugin plugin, CustomMobManager customMobManager, NamespacedKey customMobKey) {
        this.plugin = plugin;
        this.customMobManager = customMobManager;
        this.customMobKey = customMobKey;
    }

    public void start() {
        stop();
        stepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSteps, 1L, 1L);
    }

    public void stop() {
        if (stepTask != null) {
            stepTask.cancel();
            stepTask = null;
        }
    }

    public void fireSpawn(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            runTriggers(livingEntity, TriggerType.ON_SPAWN, null, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Entity attacker = event instanceof EntityDamageByEntityEvent byEntity ? attackerFrom(byEntity.getDamager()) : null;
        if (attacker instanceof LivingEntity livingAttacker && customMobId(livingAttacker).isPresent()) {
            runTriggers(livingAttacker, TriggerType.ON_HIT, target, null);
            if (isFatal(target, event.getFinalDamage())) {
                runTriggers(livingAttacker, TriggerType.ON_KILL, target, null);
            }
        }
        if (customMobId(target).isPresent()) {
            runTriggers(target, TriggerType.ON_HURT, null, attacker);
            if (isFatal(target, event.getFinalDamage())) {
                damageTriggeredDeaths.add(target.getUniqueId());
                runTriggers(target, TriggerType.ON_DEATH, null, attacker);
                Bukkit.getScheduler().runTask(plugin, () -> damageTriggeredDeaths.remove(target.getUniqueId()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (damageTriggeredDeaths.remove(entity.getUniqueId())) {
            return;
        }
        runTriggers(entity, TriggerType.ON_DEATH, null, entity.getKiller());
    }

    private void tickSteps() {
        long tick = Bukkit.getCurrentTick();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                Optional<CustomMobDefinition> definition = definitionFor(entity);
                if (definition.isEmpty()) {
                    continue;
                }
                for (MobTrigger trigger : definition.get().triggers()) {
                    if (trigger.type() == TriggerType.ON_STEP && tick % trigger.stepRateTicks() == 0) {
                        runTrigger(entity, trigger, null, null);
                    }
                }
            }
        }
    }

    private void runTriggers(LivingEntity self, TriggerType type, Entity target, Entity attacker) {
        Optional<CustomMobDefinition> definition = definitionFor(self);
        if (definition.isEmpty()) {
            return;
        }
        for (MobTrigger trigger : definition.get().triggers()) {
            if (trigger.type() == type) {
                runTrigger(self, trigger, target, attacker);
            }
        }
    }

    private void runTrigger(LivingEntity self, MobTrigger trigger, Entity target, Entity attacker) {
        for (String rawCommand : trigger.commands()) {
            String command = resolveCommand(rawCommand, self, target, attacker);
            if (command == null || command.isBlank()) {
                continue;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Trigger command failed for mob '" + customMobId(self).orElse("unknown")
                        + "' on " + trigger.type().name() + ": " + command + " (" + rootMessage(exception) + ")");
            }
        }
    }

    private String resolveCommand(String rawCommand, LivingEntity self, Entity target, Entity attacker) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return null;
        }
        if (requires(rawCommand, "TARGET") && target == null) {
            return null;
        }
        if (requires(rawCommand, "ATTACKER") && attacker == null) {
            return null;
        }
        String command = replaceRand(rawCommand);
        command = replaceEntity(command, "SELF", self);
        if (target != null) {
            command = replaceEntity(command, "TARGET", target);
        }
        if (attacker != null) {
            command = replaceEntity(command, "ATTACKER", attacker);
        }
        return command;
    }

    private String replaceRand(String command) {
        Matcher matcher = RAND_PATTERN.matcher(command);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int first = Integer.parseInt(matcher.group(1));
            int second = Integer.parseInt(matcher.group(2));
            int min = Math.min(first, second);
            int max = Math.max(first, second);
            matcher.appendReplacement(result, String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceEntity(String command, String key, Entity entity) {
        Location location = entity.getLocation();
        return command
                .replace("{" + key + "}", entity.getUniqueId().toString())
                .replace("{" + key + "_NAME}", entity.getName())
                .replace("{" + key + "_X}", format(location.getX()))
                .replace("{" + key + "_Y}", format(location.getY()))
                .replace("{" + key + "_Z}", format(location.getZ()))
                .replace("{" + key + "_WORLD}", location.getWorld() == null ? "" : location.getWorld().getName());
    }

    private boolean requires(String command, String key) {
        return command.contains("{" + key);
    }

    private String format(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private boolean isFatal(LivingEntity entity, double finalDamage) {
        return finalDamage >= entity.getHealth();
    }

    private Entity attackerFrom(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity entity ? entity : null;
        }
        return damager;
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private Optional<CustomMobDefinition> definitionFor(LivingEntity entity) {
        return customMobId(entity).flatMap(customMobManager::get);
    }

    private Optional<String> customMobId(LivingEntity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(customMobKey, PersistentDataType.STRING));
    }
}
