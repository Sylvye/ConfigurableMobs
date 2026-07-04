package com.bountysmp.configurablemobs.spawn;

import com.bountysmp.configurablemobs.data.CustomMobManager;
import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CustomSpawnerManager implements Listener {
    private static final int ACTIVATION_RANGE = 16;
    private static final int NEARBY_LIMIT = 6;
    private static final int SPAWN_RADIUS = 4;
    private static final int MIN_DELAY_TICKS = 200;
    private static final int MAX_DELAY_TICKS = 800;

    private final JavaPlugin plugin;
    private final CustomMobManager customMobManager;
    private final NamespacedKey customMobKey;
    private final NamespacedKey customSpawnerKey;
    private final File file;
    private final Map<LocationKey, SpawnerState> spawners = new HashMap<>();
    private BukkitTask task;

    public CustomSpawnerManager(
            JavaPlugin plugin,
            CustomMobManager customMobManager,
            NamespacedKey customMobKey,
            NamespacedKey customSpawnerKey) {
        this.plugin = plugin;
        this.customMobManager = customMobManager;
        this.customMobKey = customMobKey;
        this.customSpawnerKey = customSpawnerKey;
        this.file = new File(plugin.getDataFolder(), "spawners.yml");
    }

    public void load() {
        spawners.clear();
        if (!file.exists()) {
            save();
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String path : config.getKeys(false)) {
            LocationKey key = LocationKey.parse(path);
            String mobId = config.getString(path + ".mob");
            if (key != null && mobId != null) {
                spawners.put(key, new SpawnerState(mobId.toLowerCase(Locale.ROOT), nextSpawnTick()));
            }
        }
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<LocationKey, SpawnerState> entry : spawners.entrySet()) {
            config.set(entry.getKey().path() + ".mob", entry.getValue().mobId());
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save custom spawners: " + exception.getMessage());
        }
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        save();
    }

    public ItemStack createSpawnerItem(CustomMobDefinition definition) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Custom Spawner: " + definition.id());
        meta.setLore(java.util.List.of("Places a ConfigurableMobs spawner.", "Mob: " + definition.id()));
        meta.getPersistentDataContainer().set(customSpawnerKey, PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createEggItem(CustomMobDefinition definition) {
        Material material = Material.matchMaterial(definition.baseType().name() + "_SPAWN_EGG");
        if (material == null) {
            material = Material.ZOMBIE_SPAWN_EGG;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Custom Egg: " + definition.id());
        meta.setLore(java.util.List.of("Spawns custom mob: " + definition.id()));
        meta.getPersistentDataContainer().set(customMobKey, PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null) {
            return;
        }
        String mobId = event.getItem().getItemMeta().getPersistentDataContainer().get(customMobKey, PersistentDataType.STRING);
        if (mobId == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            event.getPlayer().sendMessage("Unknown custom mob: " + mobId);
            return;
        }
        event.setCancelled(true);
        Location spawnLocation = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5D, 0.0D, 0.5D);
        definition.spawn(spawnLocation, customMobKey);
        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            event.getItem().subtract();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return;
        }
        String mobId = item.getItemMeta().getPersistentDataContainer().get(customSpawnerKey, PersistentDataType.STRING);
        if (mobId == null || customMobManager.get(mobId).isEmpty()) {
            return;
        }
        LocationKey key = LocationKey.from(event.getBlockPlaced().getLocation());
        spawners.put(key, new SpawnerState(mobId, nextSpawnTick()));
        updateSpawnerBlock(event.getBlockPlaced(), mobId);
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        LocationKey key = LocationKey.from(event.getBlock().getLocation());
        SpawnerState removed = spawners.remove(key);
        if (removed == null) {
            return;
        }
        save();
        CustomMobDefinition definition = customMobManager.get(removed.mobId()).orElse(null);
        if (definition != null) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), createSpawnerItem(definition));
        }
    }

    private void tick() {
        long now = Bukkit.getCurrentTick();
        for (Map.Entry<LocationKey, SpawnerState> entry : spawners.entrySet()) {
            if (now < entry.getValue().nextSpawnTick()) {
                continue;
            }
            Location location = entry.getKey().toLocation();
            if (location == null || !location.isWorldLoaded() || location.getBlock().getType() != Material.SPAWNER) {
                entry.getValue().nextSpawnTick(nextSpawnTick());
                continue;
            }
            if (!hasNearbyPlayer(location) || nearbyCustomMobCount(location, entry.getValue().mobId()) >= NEARBY_LIMIT) {
                entry.getValue().nextSpawnTick(nextSpawnTick());
                continue;
            }
            CustomMobDefinition definition = customMobManager.get(entry.getValue().mobId()).orElse(null);
            if (definition == null) {
                entry.getValue().nextSpawnTick(nextSpawnTick());
                continue;
            }
            Location spawnLocation = randomSpawnLocation(location);
            definition.spawn(spawnLocation, customMobKey);
            entry.getValue().nextSpawnTick(nextSpawnTick());
        }
    }

    private boolean hasNearbyPlayer(Location location) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= ACTIVATION_RANGE * ACTIVATION_RANGE) {
                return true;
            }
        }
        return false;
    }

    private int nearbyCustomMobCount(Location location, String mobId) {
        int count = 0;
        for (Entity entity : location.getWorld().getNearbyEntities(location, ACTIVATION_RANGE, ACTIVATION_RANGE, ACTIVATION_RANGE)) {
            if (entity instanceof LivingEntity livingEntity) {
                String id = livingEntity.getPersistentDataContainer().get(customMobKey, PersistentDataType.STRING);
                if (mobId.equals(id)) {
                    count++;
                }
            }
        }
        return count;
    }

    private Location randomSpawnLocation(Location spawnerLocation) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return spawnerLocation.clone().add(
                random.nextInt(-SPAWN_RADIUS, SPAWN_RADIUS + 1) + 0.5D,
                0.0D,
                random.nextInt(-SPAWN_RADIUS, SPAWN_RADIUS + 1) + 0.5D);
    }

    private void updateSpawnerBlock(Block block, String mobId) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null || !(block.getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        spawner.setSpawnedType(definition.baseType());
        spawner.update(true);
    }

    private long nextSpawnTick() {
        return Bukkit.getCurrentTick() + ThreadLocalRandom.current().nextInt(MIN_DELAY_TICKS, MAX_DELAY_TICKS + 1);
    }

    private static final class SpawnerState {
        private final String mobId;
        private long nextSpawnTick;

        private SpawnerState(String mobId, long nextSpawnTick) {
            this.mobId = mobId;
            this.nextSpawnTick = nextSpawnTick;
        }

        private String mobId() {
            return mobId;
        }

        private long nextSpawnTick() {
            return nextSpawnTick;
        }

        private void nextSpawnTick(long nextSpawnTick) {
            this.nextSpawnTick = nextSpawnTick;
        }
    }
}
