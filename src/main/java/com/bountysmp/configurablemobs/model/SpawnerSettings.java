package com.bountysmp.configurablemobs.model;

import org.bukkit.configuration.ConfigurationSection;

public record SpawnerSettings(
        int spawnCount,
        int spawnRange,
        int maxNearby,
        int delayTicks,
        int randomDelayTicks,
        int requiredPlayerRange,
        boolean dropWithSilkTouch) {
    public static final SpawnerSettings DEFAULT = new SpawnerSettings(1, 4, 6, 200, 600, 16, false);

    public SpawnerSettings {
        spawnCount = clamp(spawnCount, 1, 64);
        spawnRange = clamp(spawnRange, 1, 64);
        maxNearby = clamp(maxNearby, 1, 256);
        delayTicks = clamp(delayTicks, 1, 1728000);
        randomDelayTicks = clamp(randomDelayTicks, 0, 1728000);
        requiredPlayerRange = clamp(requiredPlayerRange, 1, 128);
    }

    public static SpawnerSettings fromConfig(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        return new SpawnerSettings(
                section.getInt("spawn-count", DEFAULT.spawnCount()),
                section.getInt("spawn-range", DEFAULT.spawnRange()),
                section.getInt("max-nearby", DEFAULT.maxNearby()),
                section.getInt("delay-ticks", DEFAULT.delayTicks()),
                section.getInt("random-delay-ticks", DEFAULT.randomDelayTicks()),
                section.getInt("required-player-range", DEFAULT.requiredPlayerRange()),
                section.getBoolean("drop-with-silk-touch", DEFAULT.dropWithSilkTouch()));
    }

    public void saveTo(ConfigurationSection section) {
        section.set("spawn-count", spawnCount);
        section.set("spawn-range", spawnRange);
        section.set("max-nearby", maxNearby);
        section.set("delay-ticks", delayTicks);
        section.set("random-delay-ticks", randomDelayTicks);
        section.set("required-player-range", requiredPlayerRange);
        section.set("drop-with-silk-touch", dropWithSilkTouch);
    }

    public SpawnerSettings withSpawnCount(int value) {
        return new SpawnerSettings(value, spawnRange, maxNearby, delayTicks, randomDelayTicks, requiredPlayerRange, dropWithSilkTouch);
    }

    public SpawnerSettings withSpawnRange(int value) {
        return new SpawnerSettings(spawnCount, value, maxNearby, delayTicks, randomDelayTicks, requiredPlayerRange, dropWithSilkTouch);
    }

    public SpawnerSettings withMaxNearby(int value) {
        return new SpawnerSettings(spawnCount, spawnRange, value, delayTicks, randomDelayTicks, requiredPlayerRange, dropWithSilkTouch);
    }

    public SpawnerSettings withDelaySeconds(int value) {
        return new SpawnerSettings(spawnCount, spawnRange, maxNearby, value * 20, randomDelayTicks, requiredPlayerRange, dropWithSilkTouch);
    }

    public SpawnerSettings withRandomDelaySeconds(int value) {
        return new SpawnerSettings(spawnCount, spawnRange, maxNearby, delayTicks, value * 20, requiredPlayerRange, dropWithSilkTouch);
    }

    public SpawnerSettings withRequiredPlayerRange(int value) {
        return new SpawnerSettings(spawnCount, spawnRange, maxNearby, delayTicks, randomDelayTicks, value, dropWithSilkTouch);
    }

    public SpawnerSettings withDropWithSilkTouch(boolean value) {
        return new SpawnerSettings(spawnCount, spawnRange, maxNearby, delayTicks, randomDelayTicks, requiredPlayerRange, value);
    }

    public int delaySeconds() {
        return delayTicks / 20;
    }

    public int randomDelaySeconds() {
        return randomDelayTicks / 20;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
