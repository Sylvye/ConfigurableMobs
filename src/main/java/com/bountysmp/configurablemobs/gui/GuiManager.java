package com.bountysmp.configurablemobs.gui;

import com.bountysmp.configurablemobs.ConfigurableMobsPlugin;
import com.bountysmp.configurablemobs.data.CustomMobManager;
import com.bountysmp.configurablemobs.data.SpawnRuleManager;
import com.bountysmp.configurablemobs.model.CustomMobDefinition;
import com.bountysmp.configurablemobs.model.ReplacementRule;
import com.bountysmp.configurablemobs.model.SpawnerSettings;
import com.bountysmp.configurablemobs.model.WorldMobRule;
import com.bountysmp.configurablemobs.spawn.CustomSpawnerManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
        inventory.setItem(11, item(Material.ZOMBIE_HEAD, GuiUtil.Tone.INFO, "Custom Mobs", List.of("Create and edit custom mobs.")));
        inventory.setItem(13, item(Material.GRASS_BLOCK, GuiUtil.Tone.INFO, "Spawn Rules", List.of("Control natural spawns and replacements.")));
        inventory.setItem(15, item(Material.SPAWNER, GuiUtil.Tone.INFO, "Tools", List.of("Generate custom eggs and spawners.")));
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
            case BASE_MOB_SELECTOR -> clickBaseMobSelector(player, holder.context(), event.getSlot());
            case EQUIPMENT -> clickEquipment(player, holder.context(), event.getSlot(), event.getCursor());
            case EFFECTS -> clickEffects(player, holder.context(), event.getSlot());
            case TAGS -> clickTags(player, holder.context(), event.getSlot());
            case ATTRIBUTES -> clickAttributes(player, holder.context(), event.getSlot(), event.isRightClick());
            case TOOLS -> clickTools(player, event.getSlot(), event.isRightClick());
            case SPAWNER_EDITOR -> clickSpawnerEditor(player, holder.context(), event.getSlot());
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
            inventory.setItem(slot++, item(eggMaterial(definition.baseType()), GuiUtil.Tone.INFO,
                    definition.id(),
                    List.of("Base: " + definition.baseType().name(), "Click to edit.")));
        }
        inventory.setItem(49, item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Create Mob", List.of("Enter a new mob id.")));
        inventory.setItem(45, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
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
        inventory.setItem(10, item(eggMaterial(definition.baseType()), GuiUtil.Tone.WARNING, "Base Mob", List.of(definition.baseType().name(), "Click to choose.")));
        inventory.setItem(12, item(Material.NAME_TAG, GuiUtil.Tone.WARNING, "Display Name", List.of(definition.displayName(), "Click to edit.")));
        inventory.setItem(14, item(Material.IRON_CHESTPLATE, GuiUtil.Tone.INFO, "Equipment", List.of("Edit starting gear.")));
        inventory.setItem(16, item(Material.POTION, GuiUtil.Tone.INFO, "Potion Effects", List.of(definition.potionEffects().size() + " effects.")));
        inventory.setItem(29, item(Material.OAK_SIGN, GuiUtil.Tone.INFO, "Scoreboard Tags", List.of(definition.scoreboardTags().size() + " tags.")));
        inventory.setItem(31, item(Material.ANVIL, GuiUtil.Tone.INFO, "Attributes", List.of(definition.attributes().size() + " overrides.")));
        inventory.setItem(33, item(Material.ENDER_PEARL, GuiUtil.Tone.SUCCESS, "Test Summon", List.of("Summons at your location.")));
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickMobEditor(Player player, String mobId, int slot, ClickType click) {
        CustomMobDefinition definition = customMobManager.get(mobId).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        if (slot == 10) {
            openBaseMobSelector(player, mobId, 0, "");
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

    private void openBaseMobSelector(Player player, String mobId, int page, String query) {
        List<EntityType> filtered = filteredMobTypes(query);
        int maxPage = Math.max(0, (filtered.size() - 1) / 45);
        int safePage = Math.max(0, Math.min(page, maxPage));
        Inventory inventory = create(MenuType.BASE_MOB_SELECTOR, selectorContext(mobId, safePage, query), "Choose Base Mob", 54);
        int start = safePage * 45;
        for (int index = 0; index < 45 && start + index < filtered.size(); index++) {
            EntityType type = filtered.get(start + index);
            inventory.setItem(index, item(eggMaterial(type), GuiUtil.Tone.INFO, type.name(), List.of("Click to select.")));
        }
        inventory.setItem(45, item(Material.ARROW, GuiUtil.Tone.WARNING, "Previous Page", List.of("Page " + (safePage + 1) + " of " + (maxPage + 1))));
        inventory.setItem(48, item(Material.BARRIER, GuiUtil.Tone.DANGER, "Back", List.of()));
        inventory.setItem(49, item(Material.COMPASS, GuiUtil.Tone.WARNING, "Search", List.of(query.isBlank() ? "No filter." : "Filter: " + query)));
        inventory.setItem(53, item(Material.ARROW, GuiUtil.Tone.WARNING, "Next Page", List.of("Page " + (safePage + 1) + " of " + (maxPage + 1))));
        player.openInventory(inventory);
    }

    private void clickBaseMobSelector(Player player, String context, int slot) {
        SelectorContext selector = SelectorContext.parse(context);
        if (slot == 48) {
            openMobEditor(player, selector.mobId());
            return;
        }
        if (slot == 49) {
            promptManager.prompt(player, "Enter mob search text.", input ->
                    openBaseMobSelector(player, selector.mobId(), 0, input.replace("|", "").toLowerCase(Locale.ROOT)));
            return;
        }
        if (slot == 45) {
            openBaseMobSelector(player, selector.mobId(), selector.page() - 1, selector.query());
            return;
        }
        if (slot == 53) {
            openBaseMobSelector(player, selector.mobId(), selector.page() + 1, selector.query());
            return;
        }

        List<EntityType> filtered = filteredMobTypes(selector.query());
        int index = selector.page() * 45 + slot;
        if (slot < 0 || slot >= 45 || index >= filtered.size()) {
            return;
        }
        CustomMobDefinition definition = customMobManager.get(selector.mobId()).orElse(null);
        if (definition == null) {
            openMobList(player);
            return;
        }
        definition.baseType(filtered.get(index));
        customMobManager.save(definition);
        openMobEditor(player, selector.mobId());
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
        inventory.setItem(40, item(Material.ARMOR_STAND, GuiUtil.Tone.SUCCESS, "Copy Your Equipment", List.of("Copies your worn and held items.")));
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
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
            inventory.setItem(slot, item(Material.POTION, GuiUtil.Tone.INFO, effect.getType().getKey().asString(),
                    List.of("Duration: " + effect.getDuration() / 20 + "s", "Amplifier: " + effect.getAmplifier(), "Click to remove.")));
        }
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
        inventory.setItem(53, item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Add Effect", List.of("Format: effect seconds amplifier")));
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
            inventory.setItem(slot++, item(Material.NAME_TAG, GuiUtil.Tone.INFO, tag, List.of("Click to remove.")));
        }
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
        inventory.setItem(53, item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Add Tag", List.of("Enter a scoreboard tag.")));
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
                String defaultText = defaultAttributeText(definition.baseType(), attribute);
                inventory.setItem(slot, item(Material.ANVIL, GuiUtil.Tone.WARNING, COMMON_ATTRIBUTES.get(slot),
                        List.of("Value: " + (value == null ? "default (" + defaultText + ")" : value),
                                "Left click to set.",
                                "Right click to reset to default.")));
                if (value != null) {
                    inventory.setItem(slot + 27, item(Material.BARRIER, GuiUtil.Tone.DANGER, "Reset " + COMMON_ATTRIBUTES.get(slot),
                            List.of("Current: " + value, "Default: " + defaultText, "Click to reset.")));
                }
            }
        }
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
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
        if (slot >= 27 && slot < 27 + COMMON_ATTRIBUTES.size()) {
            Attribute attribute = CustomMobDefinition.parseAttribute(COMMON_ATTRIBUTES.get(slot - 27));
            if (attribute != null) {
                definition.attributes().remove(attribute);
                customMobManager.save(definition);
                openAttributes(player, mobId);
            }
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
            inventory.setItem(slot++, item(Material.SPAWNER, GuiUtil.Tone.INFO, definition.id(), List.of("Left click: spawn egg.", "Right click: spawner.")));
        }
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
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
        if (rightClick) {
            openSpawnerEditor(player, definition.id(), SpawnerSettings.DEFAULT);
            return;
        }
        player.getInventory().addItem(customSpawnerManager.createEggItem(definition));
        player.sendMessage("Added egg for " + definition.id() + ".");
    }

    private void openSpawnerEditor(Player player, String mobId, SpawnerSettings settings) {
        if (customMobManager.get(mobId).isEmpty()) {
            openTools(player);
            return;
        }
        Inventory inventory = create(MenuType.SPAWNER_EDITOR, spawnerContext(mobId, settings), "Spawner: " + mobId, 54);
        inventory.setItem(10, item(Material.EGG, GuiUtil.Tone.WARNING, "Spawn Count", List.of(String.valueOf(settings.spawnCount()), "Click to edit.")));
        inventory.setItem(11, item(Material.COMPASS, GuiUtil.Tone.WARNING, "Spawn Range", List.of(String.valueOf(settings.spawnRange()), "Click to edit.")));
        inventory.setItem(12, item(Material.ZOMBIE_HEAD, GuiUtil.Tone.WARNING, "Max Nearby", List.of(String.valueOf(settings.maxNearby()), "Click to edit.")));
        inventory.setItem(13, item(Material.CLOCK, GuiUtil.Tone.WARNING, "Delay", List.of(settings.delaySeconds() + " seconds", "Click to edit.")));
        inventory.setItem(14, item(Material.REPEATER, GuiUtil.Tone.WARNING, "Random Delay Range", List.of("0-" + settings.randomDelaySeconds() + " seconds", "Click to edit.")));
        inventory.setItem(15, item(Material.PLAYER_HEAD, GuiUtil.Tone.WARNING, "Required Player Range", List.of(String.valueOf(settings.requiredPlayerRange()), "Click to edit.")));
        inventory.setItem(16, item(settings.dropWithSilkTouch() ? Material.LIME_DYE : Material.GRAY_DYE,
                settings.dropWithSilkTouch() ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED,
                "Silk Touch Drop: " + (settings.dropWithSilkTouch() ? "Enabled" : "Disabled"),
                List.of("Click to toggle.", "Default: disabled.")));
        inventory.setItem(31, item(Material.SPAWNER, GuiUtil.Tone.SUCCESS, "Generate Spawner", List.of("Adds the configured spawner item.")));
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
        player.openInventory(inventory);
    }

    private void clickSpawnerEditor(Player player, String context, int slot) {
        SpawnerEditorContext editor = SpawnerEditorContext.parse(context);
        SpawnerSettings settings = editor.settings();
        if (slot == 49) {
            openTools(player);
            return;
        }
        if (slot == 31) {
            CustomMobDefinition definition = customMobManager.get(editor.mobId()).orElse(null);
            if (definition == null) {
                openTools(player);
                return;
            }
            player.getInventory().addItem(customSpawnerManager.createSpawnerItem(definition, settings));
            player.sendMessage("Added configured spawner for " + definition.id() + ".");
            openTools(player);
            return;
        }
        if (slot == 16) {
            openSpawnerEditor(player, editor.mobId(), settings.withDropWithSilkTouch(!settings.dropWithSilkTouch()));
            return;
        }
        switch (slot) {
            case 10 -> promptSpawnerInt(player, editor.mobId(), settings, "spawn count", value -> settings.withSpawnCount(value));
            case 11 -> promptSpawnerInt(player, editor.mobId(), settings, "spawn range", value -> settings.withSpawnRange(value));
            case 12 -> promptSpawnerInt(player, editor.mobId(), settings, "max nearby", value -> settings.withMaxNearby(value));
            case 13 -> promptSpawnerInt(player, editor.mobId(), settings, "delay seconds", value -> settings.withDelaySeconds(value));
            case 14 -> promptSpawnerInt(player, editor.mobId(), settings, "random delay range seconds", value -> settings.withRandomDelaySeconds(value));
            case 15 -> promptSpawnerInt(player, editor.mobId(), settings, "required player range", value -> settings.withRequiredPlayerRange(value));
            default -> {
            }
        }
    }

    private void openSpawnWorlds(Player player) {
        Inventory inventory = create(MenuType.SPAWN_WORLDS, "", "Spawn Rule Worlds", 54);
        int slot = 0;
        for (World world : Bukkit.getWorlds()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, item(Material.GRASS_BLOCK, GuiUtil.Tone.INFO, world.getName(), List.of("Click to edit world rules.")));
        }
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
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
            inventory.setItem(slot, item(eggMaterial(type), GuiUtil.Tone.INFO, type.name(), List.of("Click to configure.")));
        }
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
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
                rule.vanillaEnabled() ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.DANGER,
                "Vanilla Spawn: " + (rule.vanillaEnabled() ? "Enabled" : "Disabled"),
                List.of("Click to toggle.")));
        inventory.setItem(15, item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Add Replacement", List.of("Format: mobId chance weight")));
        for (int index = 0; index < Math.min(18, rule.replacements().size()); index++) {
            ReplacementRule replacement = rule.replacements().get(index);
            inventory.setItem(27 + index, item(Material.ZOMBIE_HEAD, GuiUtil.Tone.INFO, replacement.mobId(),
                    List.of("Chance: " + replacement.chance(), "Weight: " + replacement.weight(), "Click to remove.")));
        }
        inventory.setItem(49, item(Material.ARROW, GuiUtil.Tone.WARNING, "Back", List.of()));
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
        Inventory inventory = Bukkit.createInventory(holder, size, GuiUtil.title(title));
        holder.inventory(inventory);
        fill(inventory);
        return inventory;
    }

    private ItemStack item(Material material, GuiUtil.Tone tone, String name, List<String> lore) {
        return GuiUtil.item(material, tone, name, lore);
    }

    private void setEquipmentButton(Inventory inventory, CustomMobDefinition definition, int slot, EquipmentSlot equipmentSlot, String label) {
        ItemStack current = definition.equipment().get(equipmentSlot);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(slot, item(Material.BARRIER, GuiUtil.Tone.MUTED, label, List.of("Cursor item sets this slot.", "Empty cursor clears it.")));
            return;
        }
        inventory.setItem(slot, GuiUtil.namedClone(current, GuiUtil.Tone.INFO, label, List.of("Cursor item replaces this slot.", "Empty cursor clears it.")));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = GuiUtil.filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
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

    private List<EntityType> filteredMobTypes(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return MOB_TYPES;
        }
        return MOB_TYPES.stream()
                .filter(type -> type.name().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    private Material eggMaterial(EntityType type) {
        Material material = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return material == null ? Material.ZOMBIE_SPAWN_EGG : material;
    }

    private String selectorContext(String mobId, int page, String query) {
        return mobId + "|" + page + "|" + (query == null ? "" : query.replace("|", ""));
    }

    private String spawnerContext(String mobId, SpawnerSettings settings) {
        return String.join("|",
                mobId,
                String.valueOf(settings.spawnCount()),
                String.valueOf(settings.spawnRange()),
                String.valueOf(settings.maxNearby()),
                String.valueOf(settings.delayTicks()),
                String.valueOf(settings.randomDelayTicks()),
                String.valueOf(settings.requiredPlayerRange()),
                String.valueOf(settings.dropWithSilkTouch()));
    }

    private void promptSpawnerInt(
            Player player,
            String mobId,
            SpawnerSettings settings,
            String label,
            Function<Integer, SpawnerSettings> updater) {
        promptManager.prompt(player, "Enter " + label + " as a whole number.", input -> {
            try {
                openSpawnerEditor(player, mobId, updater.apply(Integer.parseInt(input)));
            } catch (NumberFormatException exception) {
                player.sendMessage("Value must be a whole number.");
                openSpawnerEditor(player, mobId, settings);
            }
        });
    }

    private String defaultAttributeText(EntityType type, Attribute attribute) {
        if (!type.hasDefaultAttributes()) {
            return "unavailable";
        }
        AttributeInstance instance = type.getDefaultAttributes().getAttribute(attribute);
        if (instance == null) {
            return "unavailable";
        }
        return formatDecimal(instance.getDefaultValue());
    }

    private String formatDecimal(double value) {
        String formatted = String.format(Locale.US, "%.3f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted.endsWith(".") ? formatted.substring(0, formatted.length() - 1) : formatted;
    }

    private PotionEffectType parseEffect(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        NamespacedKey key = NamespacedKey.fromString(normalized);
        return key == null ? null : Registry.EFFECT.get(key);
    }

    private record SelectorContext(String mobId, int page, String query) {
        private static SelectorContext parse(String context) {
            String[] parts = context.split("\\|", -1);
            String mobId = parts.length > 0 ? parts[0] : "";
            int page = parts.length > 1 ? parseInt(parts[1], 0) : 0;
            String query = parts.length > 2 ? parts[2] : "";
            return new SelectorContext(mobId, page, query);
        }
    }

    private record SpawnerEditorContext(String mobId, SpawnerSettings settings) {
        private static SpawnerEditorContext parse(String context) {
            String[] parts = context.split("\\|", -1);
            String mobId = parts.length > 0 ? parts[0] : "";
            SpawnerSettings defaults = SpawnerSettings.DEFAULT;
            return new SpawnerEditorContext(mobId, new SpawnerSettings(
                    parts.length > 1 ? parseInt(parts[1], defaults.spawnCount()) : defaults.spawnCount(),
                    parts.length > 2 ? parseInt(parts[2], defaults.spawnRange()) : defaults.spawnRange(),
                    parts.length > 3 ? parseInt(parts[3], defaults.maxNearby()) : defaults.maxNearby(),
                    parts.length > 4 ? parseInt(parts[4], defaults.delayTicks()) : defaults.delayTicks(),
                    parts.length > 5 ? parseInt(parts[5], defaults.randomDelayTicks()) : defaults.randomDelayTicks(),
                    parts.length > 6 ? parseInt(parts[6], defaults.requiredPlayerRange()) : defaults.requiredPlayerRange(),
                    parts.length > 7 && Boolean.parseBoolean(parts[7])));
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
