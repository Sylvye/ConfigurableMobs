package com.bountysmp.configurablemobs.data;

import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import com.bountysmp.configurablemobs.model.ReplacementRule;
import com.bountysmp.configurablemobs.model.WorldMobRule;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnRuleManager {
    private final JavaPlugin plugin;
    private final CustomMobManager customMobManager;
    private final File file;
    private final Map<String, Map<EntityType, WorldMobRule>> rules = new HashMap<>();

    public SpawnRuleManager(JavaPlugin plugin, CustomMobManager customMobManager) {
        this.plugin = plugin;
        this.customMobManager = customMobManager;
        this.file = new File(plugin.getDataFolder(), "spawn-rules.yml");
    }

    public void load() {
        rules.clear();
        if (!file.exists()) {
            save();
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection == null) {
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
            if (worldSection == null) {
                continue;
            }
            Map<EntityType, WorldMobRule> worldRules = new HashMap<>();
            ConfigurationSection mobsSection = worldSection.getConfigurationSection("mobs");
            if (mobsSection != null) {
                for (String typeName : mobsSection.getKeys(false)) {
                    EntityType type = parseEntityType(typeName);
                    ConfigurationSection mobSection = mobsSection.getConfigurationSection(typeName);
                    if (type == null || mobSection == null) {
                        continue;
                    }
                    WorldMobRule rule = new WorldMobRule();
                    rule.vanillaEnabled(mobSection.getBoolean("vanilla-enabled", true));
                    for (Map<?, ?> map : mobSection.getMapList("replacements")) {
                        Object mobId = map.get("mob");
                        Object chance = map.get("chance");
                        Object weight = map.get("weight");
                        if (mobId instanceof String id) {
                            rule.replacements().add(new ReplacementRule(
                                    id.toLowerCase(Locale.ROOT),
                                    number(chance, 0.0D),
                                    (int) number(weight, 1.0D)));
                        }
                    }
                    worldRules.put(type, rule);
                }
            }
            rules.put(worldName, worldRules);
        }
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Map<EntityType, WorldMobRule>> worldEntry : rules.entrySet()) {
            for (Map.Entry<EntityType, WorldMobRule> mobEntry : worldEntry.getValue().entrySet()) {
                String path = "worlds." + worldEntry.getKey() + ".mobs." + mobEntry.getKey().name();
                WorldMobRule rule = mobEntry.getValue();
                config.set(path + ".vanilla-enabled", rule.vanillaEnabled());
                config.set(path + ".replacements", rule.replacements().stream()
                        .map(replacement -> Map.of(
                                "mob", replacement.mobId(),
                                "chance", replacement.chance(),
                                "weight", replacement.weight()))
                        .toList());
            }
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save spawn rules: " + exception.getMessage());
        }
    }

    public WorldMobRule getOrCreate(World world, EntityType type) {
        return rules.computeIfAbsent(world.getName(), ignored -> new HashMap<>())
                .computeIfAbsent(type, ignored -> new WorldMobRule());
    }

    public Optional<WorldMobRule> get(World world, EntityType type) {
        return Optional.ofNullable(rules.getOrDefault(world.getName(), Map.of()).get(type));
    }

    public Optional<CustomMobDefinition> chooseReplacement(World world, EntityType originalType) {
        Optional<WorldMobRule> optionalRule = get(world, originalType);
        if (optionalRule.isEmpty()) {
            return Optional.empty();
        }

        List<ReplacementRule> valid = optionalRule.get().replacements().stream()
                .filter(rule -> rule.chance() > 0.0D)
                .filter(rule -> customMobManager.get(rule.mobId()).isPresent())
                .toList();
        if (valid.isEmpty()) {
            return Optional.empty();
        }

        double totalChance = Math.min(100.0D, valid.stream().mapToDouble(ReplacementRule::chance).sum());
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= totalChance) {
            return Optional.empty();
        }

        int totalWeight = valid.stream().mapToInt(ReplacementRule::weight).sum();
        int selected = ThreadLocalRandom.current().nextInt(totalWeight);
        for (ReplacementRule rule : valid) {
            selected -= rule.weight();
            if (selected < 0) {
                return customMobManager.get(rule.mobId());
            }
        }
        return Optional.empty();
    }

    private static EntityType parseEntityType(String value) {
        try {
            EntityType type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
            return type.isAlive() && type.isSpawnable() ? type : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
