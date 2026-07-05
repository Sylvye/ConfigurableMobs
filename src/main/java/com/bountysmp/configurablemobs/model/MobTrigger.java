package com.bountysmp.configurablemobs.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MobTrigger {
    public static final int DEFAULT_STEP_RATE_TICKS = 20;
    private final TriggerType type;
    private final List<String> commands = new ArrayList<>();
    private int stepRateTicks;

    public MobTrigger(TriggerType type) {
        this(type, DEFAULT_STEP_RATE_TICKS, List.of());
    }

    public MobTrigger(TriggerType type, int stepRateTicks, List<String> commands) {
        this.type = type == null ? TriggerType.ON_SPAWN : type;
        this.stepRateTicks = Math.max(1, stepRateTicks);
        if (commands != null) {
            for (String command : commands) {
                if (command != null && !command.isBlank()) {
                    this.commands.add(command);
                }
            }
        }
    }

    public static MobTrigger fromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        Object typeValue = map.get("type");
        TriggerType type = TriggerType.parse(typeValue == null ? "" : String.valueOf(typeValue));
        if (type == null) {
            return null;
        }
        int stepRateTicks = parseInt(map.get("step-rate-ticks"), DEFAULT_STEP_RATE_TICKS);
        List<String> commands = new ArrayList<>();
        Object commandsValue = map.get("commands");
        if (commandsValue instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    commands.add(String.valueOf(value));
                }
            }
        }
        return new MobTrigger(type, stepRateTicks, commands);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.name());
        if (type == TriggerType.ON_STEP) {
            map.put("step-rate-ticks", stepRateTicks);
        }
        map.put("commands", new ArrayList<>(commands));
        return map;
    }

    public String label() {
        String lower = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    public TriggerType type() {
        return type;
    }

    public List<String> commands() {
        return commands;
    }

    public int stepRateTicks() {
        return stepRateTicks;
    }

    public void stepRateTicks(int stepRateTicks) {
        this.stepRateTicks = Math.max(1, stepRateTicks);
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
