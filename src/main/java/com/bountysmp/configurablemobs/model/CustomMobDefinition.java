package com.bountysmp.configurablemobs.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

public final class CustomMobDefinition {
    private final String id;
    private String displayName;
    private EntityType baseType;
    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final List<PotionEffect> potionEffects = new ArrayList<>();
    private final Set<String> scoreboardTags = new LinkedHashSet<>();
    private final Map<Attribute, Double> attributes = new LinkedHashMap<>();

    public CustomMobDefinition(String id, String displayName, EntityType baseType) {
        this.id = id;
        this.displayName = displayName;
        this.baseType = baseType;
    }

    public static CustomMobDefinition createDefault(String id) {
        return new CustomMobDefinition(id, id, EntityType.ZOMBIE);
    }

    public static boolean isValidId(String id) {
        return id != null && id.matches("[a-z0-9_\\-]{2,48}");
    }

    public static CustomMobDefinition fromConfig(ConfigurationSection section) {
        String id = section.getString("id", "");
        EntityType baseType = parseEntityType(section.getString("base-type", "ZOMBIE"));
        CustomMobDefinition definition = new CustomMobDefinition(id, section.getString("display-name", id), baseType);

        ConfigurationSection equipmentSection = section.getConfigurationSection("equipment");
        if (equipmentSection != null) {
            for (String key : equipmentSection.getKeys(false)) {
                EquipmentSlot slot = parseEquipmentSlot(key);
                ItemStack item = equipmentSection.getItemStack(key);
                if (slot != null && item != null) {
                    definition.equipment.put(slot, item);
                }
            }
        }

        for (Object value : section.getList("potion-effects", List.of())) {
            if (value instanceof PotionEffect effect) {
                definition.potionEffects.add(effect);
            }
        }

        definition.scoreboardTags.addAll(section.getStringList("scoreboard-tags"));

        ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
        if (attributesSection != null) {
            for (String key : attributesSection.getKeys(false)) {
                Attribute attribute = parseAttribute(key);
                if (attribute != null) {
                    definition.attributes.put(attribute, attributesSection.getDouble(key));
                }
            }
        }

        return definition;
    }

    public void saveTo(ConfigurationSection section) {
        section.set("id", id);
        section.set("display-name", displayName);
        section.set("base-type", baseType.name());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            section.set("equipment." + slot.name(), equipment.get(slot));
        }

        section.set("potion-effects", potionEffects);
        section.set("scoreboard-tags", new ArrayList<>(scoreboardTags));

        for (Attribute attribute : attributes.keySet()) {
            section.set("attributes." + attributeKey(attribute), attributes.get(attribute));
        }
    }

    public Entity spawn(org.bukkit.Location location, NamespacedKey customMobKey) {
        Entity entity = location.getWorld().spawnEntity(location, baseType);
        if (entity instanceof LivingEntity livingEntity) {
            applyTo(livingEntity, customMobKey);
        }
        return entity;
    }

    public void applyTo(LivingEntity entity, NamespacedKey customMobKey) {
        entity.getPersistentDataContainer().set(customMobKey, PersistentDataType.STRING, id);
        if (displayName != null && !displayName.isBlank()) {
            entity.setCustomName(displayName);
            entity.setCustomNameVisible(true);
        }

        EntityEquipment entityEquipment = entity.getEquipment();
        if (entityEquipment != null) {
            for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
                entityEquipment.setItem(entry.getKey(), entry.getValue() == null ? null : entry.getValue().clone(), true);
            }
        }

        for (PotionEffect effect : potionEffects) {
            entity.addPotionEffect(effect);
        }

        for (String tag : scoreboardTags) {
            if (!tag.isBlank()) {
                entity.addScoreboardTag(tag);
            }
        }

        for (Map.Entry<Attribute, Double> entry : attributes.entrySet()) {
            AttributeInstance instance = entity.getAttribute(entry.getKey());
            if (instance != null) {
                instance.setBaseValue(entry.getValue());
            }
        }

        Attribute maxHealth = parseAttribute("minecraft:max_health");
        if (maxHealth != null && attributes.containsKey(maxHealth)) {
            entity.setHealth(Math.min(entity.getMaxHealth(), attributes.get(maxHealth)));
        }
    }

    public static EntityType parseEntityType(String value) {
        if (value == null) {
            return EntityType.ZOMBIE;
        }
        try {
            EntityType type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
            return isEditableType(type) ? type : EntityType.ZOMBIE;
        } catch (IllegalArgumentException ignored) {
            return EntityType.ZOMBIE;
        }
    }

    public static boolean isEditableType(EntityType type) {
        return type != null && type.isAlive() && type.isSpawnable();
    }

    public static Attribute parseAttribute(String value) {
        NamespacedKey key = parseKey(value);
        return key == null ? null : Registry.ATTRIBUTE.get(key);
    }

    public static String attributeKey(Attribute attribute) {
        return attribute.getKey().asString();
    }

    private static EquipmentSlot parseEquipmentSlot(String value) {
        try {
            return EquipmentSlot.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static NamespacedKey parseKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return NamespacedKey.fromString(normalized);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    public EntityType baseType() {
        return baseType;
    }

    public void baseType(EntityType baseType) {
        if (isEditableType(baseType)) {
            this.baseType = baseType;
        }
    }

    public Map<EquipmentSlot, ItemStack> equipment() {
        return equipment;
    }

    public List<PotionEffect> potionEffects() {
        return potionEffects;
    }

    public Set<String> scoreboardTags() {
        return scoreboardTags;
    }

    public Map<Attribute, Double> attributes() {
        return attributes;
    }
}
