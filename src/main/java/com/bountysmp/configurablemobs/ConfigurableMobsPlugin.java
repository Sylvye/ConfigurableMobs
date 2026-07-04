package com.bountysmp.configurablemobs;

import com.bountysmp.configurablemobs.command.ConfigurableMobsCommand;
import com.bountysmp.configurablemobs.data.CustomMobManager;
import com.bountysmp.configurablemobs.data.SpawnRuleManager;
import com.bountysmp.configurablemobs.gui.GuiManager;
import com.bountysmp.configurablemobs.gui.PromptManager;
import com.bountysmp.configurablemobs.spawn.CustomSpawnerManager;
import com.bountysmp.configurablemobs.spawn.NaturalSpawnListener;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigurableMobsPlugin extends JavaPlugin {
    private NamespacedKey customMobKey;
    private NamespacedKey customSpawnerKey;
    private CustomMobManager customMobManager;
    private SpawnRuleManager spawnRuleManager;
    private CustomSpawnerManager customSpawnerManager;
    private PromptManager promptManager;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        this.customMobKey = new NamespacedKey(this, "custom_mob_id");
        this.customSpawnerKey = new NamespacedKey(this, "custom_spawner_mob_id");

        this.customMobManager = new CustomMobManager(this);
        this.spawnRuleManager = new SpawnRuleManager(this, customMobManager);
        this.promptManager = new PromptManager(this);
        this.customSpawnerManager = new CustomSpawnerManager(this, customMobManager, customMobKey, customSpawnerKey);
        this.guiManager = new GuiManager(this, customMobManager, spawnRuleManager, customSpawnerManager, promptManager);

        reloadAllData();

        ConfigurableMobsCommand command = new ConfigurableMobsCommand(this, customMobManager, spawnRuleManager, guiManager);
        getCommand("configurablemobs").setExecutor(command);
        getCommand("configurablemobs").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(promptManager, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(customSpawnerManager, this);
        getServer().getPluginManager().registerEvents(new NaturalSpawnListener(spawnRuleManager, customMobKey), this);

        customSpawnerManager.start();
    }

    @Override
    public void onDisable() {
        if (customSpawnerManager != null) {
            customSpawnerManager.stop();
        }
    }

    public void reloadAllData() {
        customMobManager.load();
        spawnRuleManager.load();
        customSpawnerManager.load();
    }

    public NamespacedKey customMobKey() {
        return customMobKey;
    }

    public NamespacedKey customSpawnerKey() {
        return customSpawnerKey;
    }
}
