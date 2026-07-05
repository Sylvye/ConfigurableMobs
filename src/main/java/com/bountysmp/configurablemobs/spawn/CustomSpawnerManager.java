package com.bountysmp.configurablemobs.spawn;

import com.bountysmp.configurablemobs.data.CustomMobManager;
import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import com.bountysmp.configurablemobs.model.SpawnerSettings;
import com.bountysmp.configurablemobs.trigger.TriggerManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CustomSpawnerManager implements Listener {
    private final JavaPlugin plugin;
    private final CustomMobManager customMobManager;
    private final TriggerManager triggerManager;
    private final NamespacedKey customMobKey;
    private final NamespacedKey customSpawnerKey;
    private final NamespacedKey spawnCountKey;
    private final NamespacedKey spawnRangeKey;
    private final NamespacedKey maxNearbyKey;
    private final NamespacedKey delayTicksKey;
    private final NamespacedKey randomDelayTicksKey;
    private final NamespacedKey requiredPlayerRangeKey;
    private final NamespacedKey dropWithSilkTouchKey;
    private final File file;
    private final Map<LocationKey, SpawnerState> spawners = new HashMap<>();
    private BukkitTask task;

    public CustomSpawnerManager(
            JavaPlugin plugin,
            CustomMobManager customMobManager,
            TriggerManager triggerManager,
            NamespacedKey customMobKey,
            NamespacedKey customSpawnerKey) {
        this.plugin = plugin;
        this.customMobManager = customMobManager;
        this.triggerManager = triggerManager;
        this.customMobKey = customMobKey;
        this.customSpawnerKey = customSpawnerKey;
        this.spawnCountKey = new NamespacedKey(plugin, "spawner_spawn_count");
        this.spawnRangeKey = new NamespacedKey(plugin, "spawner_spawn_range");
        this.maxNearbyKey = new NamespacedKey(plugin, "spawner_max_nearby");
        this.delayTicksKey = new NamespacedKey(plugin, "spawner_delay_ticks");
        this.randomDelayTicksKey = new NamespacedKey(plugin, "spawner_random_delay_ticks");
        this.requiredPlayerRangeKey = new NamespacedKey(plugin, "spawner_required_player_range");
        this.dropWithSilkTouchKey = new NamespacedKey(plugin, "spawner_drop_with_silk_touch");
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
            SpawnerSettings settings = SpawnerSettings.fromConfig(config.getConfigurationSection(path + ".settings"));
            if (key != null && mobId != null) {
                spawners.put(key, new SpawnerState(mobId.toLowerCase(Locale.ROOT), settings, nextSpawnTick(settings)));
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
            entry.getValue().settings().saveTo(config.createSection(entry.getKey().path() + ".settings"));
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
        return createSpawnerItem(definition, SpawnerSettings.DEFAULT);
    }

    public ItemStack createSpawnerItem(CustomMobDefinition definition, SpawnerSettings settings) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name("Custom Spawner: " + definition.id()));
        meta.lore(lore(List.of(
                "Mob: " + definition.id(),
                "Spawn Count: " + settings.spawnCount(),
                "Spawn Range: " + settings.spawnRange(),
                "Max Nearby: " + settings.maxNearby(),
                "Delay: " + settings.delaySeconds() + "s",
                "Random Delay: 0-" + settings.randomDelaySeconds() + "s",
                "Required Player Range: " + settings.requiredPlayerRange(),
                "Silk Touch Drop: " + (settings.dropWithSilkTouch() ? "enabled" : "disabled"))));
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(customSpawnerKey, PersistentDataType.STRING, definition.id());
        writeSettings(meta, settings);
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
        meta.displayName(name("Custom Egg: " + definition.id()));
        meta.lore(lore(List.of("Spawns custom mob: " + definition.id())));
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(customMobKey, PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null) {
            return;
        }
        if (!event.getItem().hasItemMeta()) {
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
        Entity entity = definition.spawn(spawnLocation, customMobKey);
        triggerManager.fireSpawn(entity);
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
        SpawnerSettings settings = readSettings(item.getItemMeta());
        LocationKey key = LocationKey.from(event.getBlockPlaced().getLocation());
        spawners.put(key, new SpawnerState(mobId, settings, nextSpawnTick(settings)));
        updateSpawnerBlock(event.getBlockPlaced(), mobId);
        save();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        LocationKey key = LocationKey.from(event.getBlock().getLocation());
        SpawnerState removed = spawners.remove(key);
        if (removed == null) {
            return;
        }
        save();
        event.setDropItems(false);
        CustomMobDefinition definition = customMobManager.get(removed.mobId()).orElse(null);
        if (definition != null && removed.settings().dropWithSilkTouch() && hasSilkTouch(event.getPlayer().getInventory().getItemInMainHand())) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), createSpawnerItem(definition, removed.settings()));
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
                entry.getValue().nextSpawnTick(nextSpawnTick(entry.getValue().settings()));
                continue;
            }
            SpawnerSettings settings = entry.getValue().settings();
            if (!hasNearbyPlayer(location, settings.requiredPlayerRange())
                    || nearbyCustomMobCount(location, entry.getValue().mobId(), settings.spawnRange()) >= settings.maxNearby()) {
                entry.getValue().nextSpawnTick(nextSpawnTick(settings));
                continue;
            }
            CustomMobDefinition definition = customMobManager.get(entry.getValue().mobId()).orElse(null);
            if (definition == null) {
                entry.getValue().nextSpawnTick(nextSpawnTick(settings));
                continue;
            }
            int nearbyCount = nearbyCustomMobCount(location, entry.getValue().mobId(), settings.spawnRange());
            int spawnCount = Math.min(settings.spawnCount(), settings.maxNearby() - nearbyCount);
            for (int count = 0; count < spawnCount; count++) {
                Location spawnLocation = randomSpawnLocation(location, settings.spawnRange());
                Entity entity = definition.spawn(spawnLocation, customMobKey);
                triggerManager.fireSpawn(entity);
            }
            entry.getValue().nextSpawnTick(nextSpawnTick(settings));
        }
    }

    private boolean hasNearbyPlayer(Location location, int requiredPlayerRange) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= requiredPlayerRange * requiredPlayerRange) {
                return true;
            }
        }
        return false;
    }

    private int nearbyCustomMobCount(Location location, String mobId, int spawnRange) {
        int count = 0;
        for (Entity entity : location.getWorld().getNearbyEntities(location, spawnRange, spawnRange, spawnRange)) {
            if (entity instanceof LivingEntity livingEntity) {
                String id = livingEntity.getPersistentDataContainer().get(customMobKey, PersistentDataType.STRING);
                if (mobId.equals(id)) {
                    count++;
                }
            }
        }
        return count;
    }

    private Location randomSpawnLocation(Location spawnerLocation, int spawnRange) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return spawnerLocation.clone().add(
                random.nextInt(-spawnRange, spawnRange + 1) + 0.5D,
                0.0D,
                random.nextInt(-spawnRange, spawnRange + 1) + 0.5D);
    }

    private void updateSpawnerBlock(Block block, String mobId) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null || !(block.getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        spawner.setSpawnedType(definition.baseType());
        spawner.update(true);
    }

    private long nextSpawnTick(SpawnerSettings settings) {
        int extraDelay = settings.randomDelayTicks() <= 0
                ? 0
                : ThreadLocalRandom.current().nextInt(settings.randomDelayTicks() + 1);
        return Bukkit.getCurrentTick() + settings.delayTicks() + extraDelay;
    }

    private void writeSettings(ItemMeta meta, SpawnerSettings settings) {
        meta.getPersistentDataContainer().set(spawnCountKey, PersistentDataType.INTEGER, settings.spawnCount());
        meta.getPersistentDataContainer().set(spawnRangeKey, PersistentDataType.INTEGER, settings.spawnRange());
        meta.getPersistentDataContainer().set(maxNearbyKey, PersistentDataType.INTEGER, settings.maxNearby());
        meta.getPersistentDataContainer().set(delayTicksKey, PersistentDataType.INTEGER, settings.delayTicks());
        meta.getPersistentDataContainer().set(randomDelayTicksKey, PersistentDataType.INTEGER, settings.randomDelayTicks());
        meta.getPersistentDataContainer().set(requiredPlayerRangeKey, PersistentDataType.INTEGER, settings.requiredPlayerRange());
        meta.getPersistentDataContainer().set(dropWithSilkTouchKey, PersistentDataType.BYTE, (byte) (settings.dropWithSilkTouch() ? 1 : 0));
    }

    private SpawnerSettings readSettings(ItemMeta meta) {
        return new SpawnerSettings(
                getInt(meta, spawnCountKey, SpawnerSettings.DEFAULT.spawnCount()),
                getInt(meta, spawnRangeKey, SpawnerSettings.DEFAULT.spawnRange()),
                getInt(meta, maxNearbyKey, SpawnerSettings.DEFAULT.maxNearby()),
                getInt(meta, delayTicksKey, SpawnerSettings.DEFAULT.delayTicks()),
                getInt(meta, randomDelayTicksKey, SpawnerSettings.DEFAULT.randomDelayTicks()),
                getInt(meta, requiredPlayerRangeKey, SpawnerSettings.DEFAULT.requiredPlayerRange()),
                meta.getPersistentDataContainer().getOrDefault(dropWithSilkTouchKey, PersistentDataType.BYTE, (byte) 0) == 1);
    }

    private int getInt(ItemMeta meta, NamespacedKey key, int fallback) {
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
    }

    private boolean hasSilkTouch(ItemStack item) {
        return item != null && item.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    private Component name(String value) {
        return Component.text(value, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> lore(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        return components;
    }

    private static final class SpawnerState {
        private final String mobId;
        private final SpawnerSettings settings;
        private long nextSpawnTick;

        private SpawnerState(String mobId, SpawnerSettings settings, long nextSpawnTick) {
            this.mobId = mobId;
            this.settings = settings;
            this.nextSpawnTick = nextSpawnTick;
        }

        private String mobId() {
            return mobId;
        }

        private SpawnerSettings settings() {
            return settings;
        }

        private long nextSpawnTick() {
            return nextSpawnTick;
        }

        private void nextSpawnTick(long nextSpawnTick) {
            this.nextSpawnTick = nextSpawnTick;
        }
    }
}
