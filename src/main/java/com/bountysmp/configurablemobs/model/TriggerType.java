package com.bountysmp.configurablemobs.model;

import java.util.Locale;

public enum TriggerType {
    ON_HIT,
    ON_KILL,
    ON_HURT,
    ON_DEATH,
    ON_SPAWN,
    ON_STEP;

    public static TriggerType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TriggerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
