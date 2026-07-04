package com.bountysmp.configurablemobs.data;

import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomMobManager {
    private final JavaPlugin plugin;
    private final File mobsFolder;
    private final Map<String, CustomMobDefinition> mobs = new LinkedHashMap<>();

    public CustomMobManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mobsFolder = new File(plugin.getDataFolder(), "mobs");
    }

    public void load() {
        mobs.clear();
        if (!mobsFolder.exists() && !mobsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create mobs folder.");
            return;
        }

        File[] files = mobsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            CustomMobDefinition definition = CustomMobDefinition.fromConfig(config);
            if (CustomMobDefinition.isValidId(definition.id())) {
                mobs.put(definition.id(), definition);
            } else {
                plugin.getLogger().warning("Skipping custom mob with invalid id in " + file.getName());
            }
        }
    }

    public void save(CustomMobDefinition definition) {
        if (!mobsFolder.exists() && !mobsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create mobs folder.");
            return;
        }
        mobs.put(definition.id(), definition);
        File file = fileFor(definition.id());
        YamlConfiguration config = new YamlConfiguration();
        definition.saveTo(config);
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save custom mob " + definition.id() + ": " + exception.getMessage());
        }
    }

    public Optional<CustomMobDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mobs.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<CustomMobDefinition> all() {
        return mobs.values().stream()
                .sorted(Comparator.comparing(CustomMobDefinition::id))
                .toList();
    }

    public CustomMobDefinition create(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        CustomMobDefinition definition = CustomMobDefinition.createDefault(normalized);
        save(definition);
        return definition;
    }

    private File fileFor(String id) {
        return new File(mobsFolder, id + ".yml");
    }
}
