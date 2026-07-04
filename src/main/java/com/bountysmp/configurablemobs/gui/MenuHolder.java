package com.bountysmp.configurablemobs.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    private final MenuType type;
    private final String context;
    private Inventory inventory;

    public MenuHolder(MenuType type, String context) {
        this.type = type;
        this.context = context;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuType type() {
        return type;
    }

    public String context() {
        return context;
    }
}
