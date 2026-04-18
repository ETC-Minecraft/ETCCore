package com.etcmc.etccore.manager;

import com.etcmc.etccore.ETCCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class FeatureConfigManager {

    private final File onJoinFile;
    private final File deathRespawnFile;
    private final File teleportFile;
    private final File playtimeFile;

    private YamlConfiguration onJoinConfig;
    private YamlConfiguration deathRespawnConfig;
    private YamlConfiguration teleportConfig;
    private YamlConfiguration playtimeConfig;

    public FeatureConfigManager(ETCCore plugin) {
        onJoinFile = new File(plugin.getDataFolder(), "on-join.yml");
        deathRespawnFile = new File(plugin.getDataFolder(), "death-respawn.yml");
        teleportFile = new File(plugin.getDataFolder(), "teleport.yml");
        playtimeFile = new File(plugin.getDataFolder(), "playtime.yml");

        if (!onJoinFile.exists()) {
            plugin.saveResource("on-join.yml", false);
        }
        if (!deathRespawnFile.exists()) {
            plugin.saveResource("death-respawn.yml", false);
        }
        if (!teleportFile.exists()) {
            plugin.saveResource("teleport.yml", false);
        }
        if (!playtimeFile.exists()) {
            plugin.saveResource("playtime.yml", false);
        }

        reload();
    }

    public void reload() {
        onJoinConfig = YamlConfiguration.loadConfiguration(onJoinFile);
        deathRespawnConfig = YamlConfiguration.loadConfiguration(deathRespawnFile);
        teleportConfig = YamlConfiguration.loadConfiguration(teleportFile);
        playtimeConfig = YamlConfiguration.loadConfiguration(playtimeFile);
    }

    public YamlConfiguration getOnJoinConfig() {
        return onJoinConfig;
    }

    public YamlConfiguration getDeathRespawnConfig() {
        return deathRespawnConfig;
    }

    public YamlConfiguration getTeleportConfig() {
        return teleportConfig;
    }

    public YamlConfiguration getPlaytimeConfig() {
        return playtimeConfig;
    }
}