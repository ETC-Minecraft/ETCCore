package com.etcmc.etccore.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages periodic YAML-defined server tasks from commands/scheduled/*.yml
 *
 * Each file defines one task. Example (disabled by default):
 * <pre>
 * enabled: false
 * interval: 3600       # seconds between executions (minimum: 1s)
 * actions:
 *   - "[BROADCAST] &aMensaje automático del servidor."
 *   - "[CONSOLE] save-all"
 * </pre>
 *
 * Supported actions in global tasks:
 *   [BROADCAST] &aTexto    — broadcasts to all online players
 *   [CONSOLE] comando      — runs a command as console
 *
 * Actions requiring a player context ([MESSAGE], [TITLE], [SOUND], etc.)
 * are silently skipped since there is no player associated with a global task.
 *
 * Reload with /etccore reload.
 */
public class ScheduledTaskManager {

    private final JavaPlugin plugin;
    private final Map<String, io.papermc.paper.threadedregions.scheduler.ScheduledTask> activeTasks
            = new HashMap<>();

    public ScheduledTaskManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cancelAll();
        File scheduledDir = new File(plugin.getDataFolder(), "commands/scheduled");
        if (!scheduledDir.exists()) scheduledDir.mkdirs();

        File[] files = scheduledDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) return;

        for (File file : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                if (!cfg.getBoolean("enabled", false)) continue;

                long intervalSecs = cfg.getLong("interval", 3600);
                List<String> actions = cfg.getStringList("actions");
                if (actions.isEmpty()) continue;

                long ticks    = Math.max(intervalSecs * 20L, 20L);
                String name   = file.getName().replace(".yml", "");
                List<String> snapshot = new ArrayList<>(actions);

                var task = plugin.getServer().getGlobalRegionScheduler()
                        .runAtFixedRate(plugin, t -> runActions(name, snapshot), ticks, ticks);

                activeTasks.put(name, task);
                plugin.getLogger().info("[ScheduledTasks] Tarea activa: '"
                        + name + "' cada " + intervalSecs + "s.");

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[ScheduledTasks] Error al cargar: " + file.getName(), e);
            }
        }
    }

    private void runActions(String taskName, List<String> actions) {
        for (String action : actions) {
            try {
                if (action.startsWith("[CONSOLE]")) {
                    String cmd = action.substring(9).trim();
                    plugin.getServer().getGlobalRegionScheduler().run(plugin,
                            t -> plugin.getServer().dispatchCommand(
                                    plugin.getServer().getConsoleSender(), cmd));

                } else if (action.startsWith("[BROADCAST]")) {
                    String msg = action.substring(11).trim().replace("&", "§");
                    plugin.getServer().getGlobalRegionScheduler().run(plugin,
                            t -> plugin.getServer().broadcastMessage(msg));
                }
                // Player-specific actions are not supported in global tasks
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[ScheduledTasks] Error en tarea '" + taskName + "': " + action, e);
            }
        }
    }

    public void reload() {
        load();
    }

    public void shutdown() {
        cancelAll();
    }

    private void cancelAll() {
        activeTasks.values().forEach(t -> {
            try { t.cancel(); } catch (Exception ignored) {}
        });
        activeTasks.clear();
    }
}
