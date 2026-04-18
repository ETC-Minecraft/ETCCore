package com.etcmc.etccore.manager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Appends command-execution records to plugins/ETCCore/logs/commands.log
 *
 * Format:  [2026-04-13 10:30:00] player:Steve world:world command:/spawn args:(none)
 *
 * Enable via config.yml:
 *   log-commands: true
 *
 * All disk writes are async — no main-thread impact.
 */
public class CommandLogger {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final File       logFile;
    private boolean          enabled;

    public CommandLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) logsDir.mkdirs();
        this.logFile = new File(logsDir, "commands.log");
        this.enabled = plugin.getConfig().getBoolean("log-commands", false);
    }

    /** Call after /etccore reload to pick up config changes. */
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("log-commands", false);
    }

    /**
     * Log an execution entry asynchronously.
     *
     * @param player  the player who ran the command
     * @param command the command name (without /)
     * @param args    the arguments passed
     */
    public void log(Player player, String command, String[] args) {
        if (!enabled) return;
        String entry = String.format("[%s] player:%s world:%s command:/%s args:%s%n",
                LocalDateTime.now().format(FMT),
                player.getName(),
                player.getWorld().getName(),
                command,
                args.length > 0 ? String.join(" ", args) : "(none)");

        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
                bw.write(entry);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[CommandLogger] Error al escribir en commands.log", e);
            }
        });
    }
}
