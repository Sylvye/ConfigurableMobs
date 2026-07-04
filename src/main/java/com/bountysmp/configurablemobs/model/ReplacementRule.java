package com.bountysmp.configurablemobs.model;

public record ReplacementRule(String mobId, double chance, int weight) {
    public ReplacementRule {
        chance = Math.max(0.0D, Math.min(100.0D, chance));
        weight = Math.max(1, weight);
    }
}
