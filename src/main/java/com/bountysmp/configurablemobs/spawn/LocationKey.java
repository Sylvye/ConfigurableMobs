package com.bountysmp.configurablemobs.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record LocationKey(String world, int x, int y, int z) {
    public static LocationKey from(Location location) {
        return new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public String path() {
        return world + "," + x + "," + y + "," + z;
    }

    public static LocationKey parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new LocationKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
