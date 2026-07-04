package com.bountysmp.configurablemobs.gui;

import com.bountysmp.configurablemobs.ConfigurableMobsPlugin;
import com.bountysmp.configurablemobs.data.CustomMobManager;
import com.bountysmp.configurablemobs.data.SpawnRuleManager;
import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import com.bountysmp.configurablemobs.model.ReplacementRule;
import com.bountysmp.configurablemobs.model.WorldMobRule;
import com.bountysmp.configurablemobs.spawn.CustomSpawnerManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class GuiManager implements Listener {
    private static final List<EntityType> MOB_TYPES = List.of(EntityType.values()).stream()
            .filter(CustomMobDefinition::isEditableType)
            .sorted(Comparator.comparing(EntityType::name))
            .toList();
    private static final List<String> COMMON_ATTRIBUTES = List.of(
            "minecraft:max_health",
            "minecraft:movement_speed",
            "minecraft:attack_damage",
            "minecraft:attack_speed",
            "minecraft:armor",
            "minecraft:armor_toughness",
            "minecraft:knockback_resistance",
            "minecraft:follow_range",
            "minecraft:scale",
            "minecraft:step_height",
            "minecraft:gravity");
    private final ConfigurableMobsPlugin plugin;
    private final CustomMobManager customMobManager;
    private final SpawnRuleManager spawnRuleManager;
    private final CustomSpawnerManager customSpawnerManager;
    private final PromptManager promptManager;

    public GuiManager(
            ConfigurableMobsPlugin plugin,
            CustomMobManager customMobManager,
            SpawnRuleManager spawnRuleManager,
            CustomSpawnerManager customSpawnerManager,
            PromptManager promptManager) {
        this.plugin = plugin;
        this.customMobManager = customMobManager;
        this.spawnRuleManager = spawnRuleManager;
        this.customSpawnerManager = customSpawnerManager;
        this.promptManager = promptManager;
    }

    public void openMain(Player player) {
        Inventory inventory = create(MenuType.MAIN, "", "ConfigurableMobs", 27);
        inventory.setItem(11, item(Material.ZOMBIE_HEAD, "Custom Mobs", List.of("Create and edit custom mobs.")));
        inventory.setItem(13, item(Material.GRASS_BLOCK, "Spawn Rules", List.of("Control natural spawns and replacements.")));
        inventory.setItem(15, item(Material.SPAWNER, "Tools", List.of("Generate custom eggs and spawners.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("configurablemobs.admin")) {
            player.closeInventory();
            return;
        }

        switch (holder.type()) {
            case MAIN -> clickMain(player, event.getSlot());
            case MOB_LIST -> clickMobList(player, event.getSlot());
            case MOB_EDITOR -> clickMobEditor(player, holder.context(), event.getSlot(), event.getClick());
            case EQUIPMENT -> clickEquipment(player, holder.context(), event.getSlot(), event.getCursor());
            case EFFECTS -> clickEffects(player, holder.context(), event.getSlot());
            case TAGS -> clickTags(player, holder.context(), event.getSlot());
            case ATTRIBUTES -> clickAttributes(player, holder.context(), event.getSlot(), event.isRightClick());
            case TOOLS -> clickTools(player, event.getSlot(), event.isRightClick());
            case SPAWN_WORLDS -> clickSpawnWorlds(player, event.getSlot());
            case SPAWN_MOBS -> clickSpawnMobs(player, holder.context(), event.getSlot());
            case SPAWN_RULE -> clickSpawnRule(player, holder.context(), event.getSlot());
        }
    }

    private void clickMain(Player player, int slot) {
        if (slot == 11) {
            openMobList(player);
        } else if (slot == 13) {
            openSpawnWorlds(player);
        } else if (slot == 15) {
            openTools(player);
        }
    }

    private void openMobList(Player player) {
        Inventory inventory = create(MenuType.MOB_LIST, "", "Custom Mobs", 54);
        int slot = 0;
        for (CustomMobDefinition definition : customMobManager.all()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(Material.matchMaterial(definition.baseType().name() + "_SPAWN_EGG") == null
                    ? Material.ZOMBIE_SPAWN_EGG
                    : Material.matchMaterial(definition.baseType().name() + "_SPAWN_EGG"),
                    definition.id(),
                    List.of("Base: " + definition.baseType().name(), "Click to edit.")));
        }
        inventory.setItem(49, item(Material.EMERALD, "Create Mob", List.of("Enter a new mob id.")));
        inventory.setItem(45, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickMobList(Player player, int slot) {
        if (slot == 45) {
            openMain(player);
            return;
        }
        if (slot == 49) {
            promptManager.prompt(player, "Enter a mob id using lowercase letters, numbers, hyphen, or underscore.", input -> {
                String id = input.toLowerCase(Locale.ROOT);
                if (!CustomMobDefinition.isValidId(id)) {
                    player.sendMessage("Invalid mob id.");
                    openMobList(player);
                    return;
                }
                if (customMobManager.get(id).isPresent()) {
                    player.sendMessage("That mob id already exists.");
                    openMobList(player);
                    return;
                }
                customMobManager.create(id);
                openMobEditor(player, id);
            });
            return;
        }
        CustomMobDefinition definition = customMobAt(slot);
        if (definition != null) {
            openMobEditor(player, definition.id());
        }
    }

    private void openMobEditor(Player player, String mobId) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        Inventory inventory = create(MenuType.MOB_EDITOR, mobId, "Mob: " + mobId, 54);
        inventory.setItem(10, item(Material.ZOMBIE_HEAD, "Base Mob", List.of(definition.baseType().name(), "Left/right click to cycle.")));
        inventory.setItem(12, item(Material.NAME_TAG, "Display Name", List.of(definition.displayName(), "Click to edit.")));
        inventory.setItem(14, item(Material.IRON_CHESTPLATE, "Equipment", List.of("Edit starting gear.")));
        inventory.setItem(16, item(Material.POTION, "Potion Effects", List.of(definition.potionEffects().size() + " effects.")));
        inventory.setItem(29, item(Material.OAK_SIGN, "Scoreboard Tags", List.of(definition.scoreboardTags().size() + " tags.")));
        inventory.setItem(31, item(Material.ANVIL, "Attributes", List.of(definition.attributes().size() + " overrides.")));
        inventory.setItem(33, item(Material.ENDER_PEARL, "Test Summon", List.of("Summons at your location.")));
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickMobEditor(Player player, String mobId, int slot, ClickType click) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        if (slot == 10) {
            int current = MOB_TYPES.indexOf(definition.baseType());
            int next = click == ClickType.RIGHT ? current - 1 : current + 1;
            if (next < 0) {
                next = MOB_TYPES.size() - 1;
            } else if (next >= MOB_TYPES.size()) {
                next = 0;
            }
            definition.baseType(MOB_TYPES.get(next));
            customMobManager.save(definition);
            openMobEditor(player, mobId);
        } else if (slot == 12) {
            promptManager.prompt(player, "Enter display name.", input -> {
                definition.displayName(ChatColor.translateAlternateColorCodes('&', input));
                customMobManager.save(definition);
                openMobEditor(player, mobId);
            });
        } else if (slot == 14) {
            openEquipment(player, mobId);
        } else if (slot == 16) {
            openEffects(player, mobId);
        } else if (slot == 29) {
            openTags(player, mobId);
        } else if (slot == 31) {
            openAttributes(player, mobId);
        } else if (slot == 33) {
            definition.spawn(player.getLocation(), plugin.customMobKey());
            player.sendMessage("Summoned " + mobId + ".");
        } else if (slot == 49) {
            openMobList(player);
        }
    }

    private void openEquipment(Player player, String mobId) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        Inventory inventory = create(MenuType.EQUIPMENT, mobId, "Equipment: " + mobId, 54);
        setEquipmentButton(inventory, definition, 10, EquipmentSlot.HAND, "Main Hand");
        setEquipmentButton(inventory, definition, 12, EquipmentSlot.OFF_HAND, "Off Hand");
        setEquipmentButton(inventory, definition, 14, EquipmentSlot.HEAD, "Helmet");
        setEquipmentButton(inventory, definition, 19, EquipmentSlot.CHEST, "Chestplate");
        setEquipmentButton(inventory, definition, 21, EquipmentSlot.LEGS, "Leggings");
        setEquipmentButton(inventory, definition, 23, EquipmentSlot.FEET, "Boots");
        inventory.setItem(40, item(Material.ARMOR_STAND, "Copy Your Equipment", List.of("Copies your worn and held items.")));
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickEquipment(Player player, String mobId, int slot, ItemStack cursor) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        EquipmentSlot equipmentSlot = switch (slot) {
            case 10 -> EquipmentSlot.HAND;
            case 12 -> EquipmentSlot.OFF_HAND;
            case 14 -> EquipmentSlot.HEAD;
            case 19 -> EquipmentSlot.CHEST;
            case 21 -> EquipmentSlot.LEGS;
            case 23 -> EquipmentSlot.FEET;
            default -> null;
        };
        if (equipmentSlot != null) {
            if (cursor == null || cursor.getType().isAir()) {
                definition.equipment().remove(equipmentSlot);
                player.sendMessage("Cleared " + equipmentSlot.name() + ".");
            } else {
                definition.equipment().put(equipmentSlot, cursor.clone());
                player.sendMessage("Set " + equipmentSlot.name() + " from cursor item.");
            }
            customMobManager.save(definition);
            openEquipment(player, mobId);
            return;
        }
        if (slot == 40) {
            PlayerInventory inventory = player.getInventory();
            copyEquipment(definition, EquipmentSlot.HAND, inventory.getItemInMainHand());
            copyEquipment(definition, EquipmentSlot.OFF_HAND, inventory.getItemInOffHand());
            copyEquipment(definition, EquipmentSlot.HEAD, inventory.getHelmet());
            copyEquipment(definition, EquipmentSlot.CHEST, inventory.getChestplate());
            copyEquipment(definition, EquipmentSlot.LEGS, inventory.getLeggings());
            copyEquipment(definition, EquipmentSlot.FEET, inventory.getBoots());
            customMobManager.save(definition);
            openEquipment(player, mobId);
        } else if (slot == 49) {
            openMobEditor(player, mobId);
        }
    }

    private void openEffects(Player player, String mobId) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        Inventory inventory = create(MenuType.EFFECTS, mobId, "Effects: " + mobId, 54);
        for (int slot = 0; slot < Math.min(45, definition.potionEffects().size()); slot++) {
            PotionEffect effect = definition.potionEffects().get(slot);
            inventory.setItem(slot, item(Material.POTION, effect.getType().getKey().asString(),
                    List.of("Duration: " + effect.getDuration() / 20 + "s", "Amplifier: " + effect.getAmplifier(), "Click to remove.")));
        }
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        inventory.setItem(53, item(Material.EMERALD, "Add Effect", List.of("Format: effect seconds amplifier")));
        player.openInventory(inventory);
    }

    private void clickEffects(Player player, String mobId, int slot) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        if (slot == 49) {
            openMobEditor(player, mobId);
        } else if (slot == 53) {
            promptManager.prompt(player, "Enter effect seconds amplifier. Example: speed 60 1", input -> {
                String[] parts = input.split("\\s+");
                if (parts.length != 3) {
                    player.sendMessage("Expected: effect seconds amplifier");
                    openEffects(player, mobId);
                    return;
                }
                PotionEffectType type = parseEffect(parts[0]);
                if (type == null) {
                    player.sendMessage("Unknown effect: " + parts[0]);
                    openEffects(player, mobId);
                    return;
                }
                try {
                    int seconds = Integer.parseInt(parts[1]);
                    int amplifier = Integer.parseInt(parts[2]);
                    definition.potionEffects().add(new PotionEffect(type, Math.max(1, seconds) * 20, Math.max(0, amplifier)));
                    customMobManager.save(definition);
                } catch (NumberFormatException exception) {
                    player.sendMessage("Seconds and amplifier must be numbers.");
                }
                openEffects(player, mobId);
            });
        } else if (slot >= 0 && slot < definition.potionEffects().size()) {
            definition.potionEffects().remove(slot);
            customMobManager.save(definition);
            openEffects(player, mobId);
        }
    }

    private void openTags(Player player, String mobId) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        Inventory inventory = create(MenuType.TAGS, mobId, "Tags: " + mobId, 54);
        int slot = 0;
        for (String tag : definition.scoreboardTags()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(Material.NAME_TAG, tag, List.of("Click to remove.")));
        }
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        inventory.setItem(53, item(Material.EMERALD, "Add Tag", List.of("Enter a scoreboard tag.")));
        player.openInventory(inventory);
    }

    private void clickTags(Player player, String mobId, int slot) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        if (slot == 49) {
            openMobEditor(player, mobId);
        } else if (slot == 53) {
            promptManager.prompt(player, "Enter scoreboard tag.", input -> {
                if (input.length() > 64 || input.isBlank()) {
                    player.sendMessage("Tag must be 1-64 characters.");
                } else {
                    definition.scoreboardTags().add(input);
                    customMobManager.save(definition);
                }
                openTags(player, mobId);
            });
        } else {
            List<String> tags = new ArrayList<>(definition.scoreboardTags());
            if (slot >= 0 && slot < tags.size()) {
                definition.scoreboardTags().remove(tags.get(slot));
                customMobManager.save(definition);
                openTags(player, mobId);
            }
        }
    }

    private void openAttributes(Player player, String mobId) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        Inventory inventory = create(MenuType.ATTRIBUTES, mobId, "Attributes: " + mobId, 54);
        for (int slot = 0; slot < COMMON_ATTRIBUTES.size(); slot++) {
            Attribute attribute = CustomMobDefinition.parseAttribute(COMMON_ATTRIBUTES.get(slot));
            if (attribute != null) {
                Double value = definition.attributes().get(attribute);
                inventory.setItem(slot, item(Material.ANVIL, COMMON_ATTRIBUTES.get(slot),
                        List.of("Value: " + (value == null ? "default" : value), "Left click to set.", "Right click to clear.")));
            }
        }
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickAttributes(Player player, String mobId, int slot, boolean rightClick) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        if (slot == 49) {
            openMobEditor(player, mobId);
            return;
        }
        if (slot < 0 || slot >= COMMON_ATTRIBUTES.size()) {
            return;
        }
        Attribute attribute = CustomMobDefinition.parseAttribute(COMMON_ATTRIBUTES.get(slot));
        if (attribute == null) {
            return;
        }
        if (rightClick) {
            definition.attributes().remove(attribute);
            customMobManager.save(definition);
            openAttributes(player, mobId);
            return;
        }
        promptManager.prompt(player, "Enter numeric value for " + COMMON_ATTRIBUTES.get(slot) + ".", input -> {
            try {
                definition.attributes().put(attribute, Double.parseDouble(input));
                customMobManager.save(definition);
            } catch (NumberFormatException exception) {
                player.sendMessage("Value must be a number.");
            }
            openAttributes(player, mobId);
        });
    }

    private void openTools(Player player) {
        Inventory inventory = create(MenuType.TOOLS, "", "Custom Mob Tools", 54);
        int slot = 0;
        for (CustomMobDefinition definition : customMobManager.all()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(Material.SPAWNER, definition.id(), List.of("Left click: spawn egg.", "Right click: spawner.")));
        }
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickTools(Player player, int slot, boolean rightClick) {
        if (slot == 49) {
            openMain(player);
            return;
        }
        CustomMobDefinition definition = customMobAt(slot);
        if (definition == null) {
            return;
        }
        player.getInventory().addItem(rightClick
                ? customSpawnerManager.createSpawnerItem(definition)
                : customSpawnerManager.createEggItem(definition));
        player.sendMessage("Added " + (rightClick ? "spawner" : "egg") + " for " + definition.id() + ".");
    }

    private void openSpawnWorlds(Player player) {
        Inventory inventory = create(MenuType.SPAWN_WORLDS, "", "Spawn Rule Worlds", 54);
        int slot = 0;
        for (World world : Bukkit.getWorlds()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(Material.GRASS_BLOCK, world.getName(), List.of("Click to edit world rules.")));
        }
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickSpawnWorlds(Player player, int slot) {
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot >= 0 && slot < Bukkit.getWorlds().size()) {
            openSpawnMobs(player, Bukkit.getWorlds().get(slot).getName());
        }
    }

    private void openSpawnMobs(Player player, String worldName) {
        Inventory inventory = create(MenuType.SPAWN_MOBS, worldName, "Spawn Mobs: " + worldName, 54);
        for (int slot = 0; slot < Math.min(45, MOB_TYPES.size()); slot++) {
            EntityType type = MOB_TYPES.get(slot);
            inventory.setItem(slot, item(Material.EGG, type.name(), List.of("Click to configure.")));
        }
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickSpawnMobs(Player player, String worldName, int slot) {
        if (slot == 49) {
            openSpawnWorlds(player);
            return;
        }
        if (slot >= 0 && slot < MOB_TYPES.size()) {
            openSpawnRule(player, worldName, MOB_TYPES.get(slot));
        }
    }

    private void openSpawnRule(Player player, String worldName, EntityType type) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            openSpawnWorlds(player);
            return;
        }
        WorldMobRule rule = spawnRuleManager.getOrCreate(world, type);
        Inventory inventory = create(MenuType.SPAWN_RULE, worldName + "|" + type.name(), "Rule: " + type.name(), 54);
        inventory.setItem(11, item(rule.vanillaEnabled() ? Material.LIME_DYE : Material.RED_DYE,
                "Vanilla Spawn: " + (rule.vanillaEnabled() ? "Enabled" : "Disabled"),
                List.of("Click to toggle.")));
        inventory.setItem(15, item(Material.EMERALD, "Add Replacement", List.of("Format: mobId chance weight")));
        for (int index = 0; index < Math.min(18, rule.replacements().size()); index++) {
            ReplacementRule replacement = rule.replacements().get(index);
            inventory.setItem(27 + index, item(Material.ZOMBIE_HEAD, replacement.mobId(),
                    List.of("Chance: " + replacement.chance(), "Weight: " + replacement.weight(), "Click to remove.")));
        }
        inventory.setItem(49, item(Material.ARROW, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickSpawnRule(Player player, String context, int slot) {
        String[] parts = context.split("\\|");
        World world = Bukkit.getWorld(parts[0]);
        EntityType type = EntityType.valueOf(parts[1]);
        if (world == null) {
            openSpawnWorlds(player);
            return;
        }
        WorldMobRule rule = spawnRuleManager.getOrCreate(world, type);
        if (slot == 49) {
            openSpawnMobs(player, world.getName());
        } else if (slot == 11) {
            rule.vanillaEnabled(!rule.vanillaEnabled());
            spawnRuleManager.save();
            openSpawnRule(player, world.getName(), type);
        } else if (slot == 15) {
            promptManager.prompt(player, "Enter mobId chance weight. Example: zombie_guard 25 1", input -> {
                String[] values = input.split("\\s+");
                if (values.length != 3 || customMobManager.get(values[0]).isEmpty()) {
                    player.sendMessage("Expected an existing mob id, chance, and weight.");
                    openSpawnRule(player, world.getName(), type);
                    return;
                }
                try {
                    rule.replacements().add(new ReplacementRule(values[0].toLowerCase(Locale.ROOT), Double.parseDouble(values[1]), Integer.parseInt(values[2])));
                    spawnRuleManager.save();
                } catch (NumberFormatException exception) {
                    player.sendMessage("Chance and weight must be numbers.");
                }
                openSpawnRule(player, world.getName(), type);
            });
        } else if (slot >= 27 && slot < 45) {
            int index = slot - 27;
            if (index < rule.replacements().size()) {
                rule.replacements().remove(index);
                spawnRuleManager.save();
                openSpawnRule(player, world.getName(), type);
            }
        }
    }

    private Inventory create(MenuType type, String context, String title, int size) {
        MenuHolder holder = new MenuHolder(type, context);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory(inventory);
        return inventory;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + name);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void setEquipmentButton(Inventory inventory, CustomMobDefinition definition, int slot, EquipmentSlot equipmentSlot, String label) {
        ItemStack current = definition.equipment().get(equipmentSlot);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(slot, item(Material.BARRIER, label, List.of("Cursor item sets this slot.", "Empty cursor clears it.")));
            return;
        }
        ItemStack display = current.clone();
        ItemMeta meta = display.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + label);
        display.setItemMeta(meta);
        inventory.setItem(slot, display);
    }

    private void copyEquipment(CustomMobDefinition definition, EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            definition.equipment().remove(slot);
        } else {
            definition.equipment().put(slot, item.clone());
        }
    }

    private CustomMobDefinition customMobAt(int slot) {
        if (slot < 0) {
            return null;
        }
        List<CustomMobDefinition> mobs = new ArrayList<>(customMobManager.all());
        return slot < mobs.size() ? mobs.get(slot) : null;
    }

    private PotionEffectType parseEffect(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        NamespacedKey key = NamespacedKey.fromString(normalized);
        return key == null ? null : Registry.EFFECT.get(key);
    }
}
