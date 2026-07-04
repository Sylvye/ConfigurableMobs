package com.bountysmp.configurablemobs.model;

import java.util.ArrayList;
import java.util.List;

public final class WorldMobRule {
    private boolean vanillaEnabled = true;
    private final List<ReplacementRule> replacements = new ArrayList<>();

    public boolean vanillaEnabled() {
        return vanillaEnabled;
    }

    public void vanillaEnabled(boolean vanillaEnabled) {
        this.vanillaEnabled = vanillaEnabled;
    }

    public List<ReplacementRule> replacements() {
        return replacements;
    }
}
