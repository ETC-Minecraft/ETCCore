package dev.foliacmds;

import dev.foliacmds.command.EnderChestCommand;
import dev.foliacmds.command.ReloadCommand;
import dev.foliacmds.manager.CommandManager;
import dev.foliacmds.manager.CooldownManager;
import dev.foliacmds.manager.FileWatcher;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class FoliaCustomCommands extends JavaPlugin {

    private CommandManager commandManager;
    private CooldownManager cooldownManager;
    private FileWatcher fileWatcher;
    private Thread watcherThread;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultCommands();

        cooldownManager = new CooldownManager();
        commandManager  = new CommandManager(this);
        commandManager.loadCommands();

        // Limpiar cooldowns expirados cada 5 minutos
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,
                t -> cooldownManager.clearExpired(), 6000L, 6000L);

        // Auto-reload al guardar archivos en commands/
        if (getConfig().getBoolean("auto-reload", true)) {
            fileWatcher = new FileWatcher(this);
            watcherThread = new Thread(fileWatcher, "FCC-FileWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
        }

        var fccCmd = getCommand("fccmds");
        if (fccCmd != null) {
            fccCmd.setExecutor(new ReloadCommand(this));
        }

        var ecCmd = getCommand("enderchest");
        if (ecCmd != null) {
            EnderChestCommand ec = new EnderChestCommand(this);
            ecCmd.setExecutor(ec);
            ecCmd.setTabCompleter(ec);
            getServer().getPluginManager().registerEvents(ec, this);
        }

        getLogger().info("FoliaCustomCommands habilitado — "
                + commandManager.getCommandCount() + " comando(s) cargado(s).");
    }

    private void saveDefaultCommands() {
        File commandsDir = new File(getDataFolder(), "commands");
        if (!commandsDir.exists()) {
            commandsDir.mkdirs();
            saveResource("commands/survival.yml", false);
            saveResource("commands/inicio.yml", false);
        }
    }

    @Override
    public void onDisable() {
        if (fileWatcher != null) fileWatcher.stop();
        if (watcherThread != null) watcherThread.interrupt();
        getLogger().info("FoliaCustomCommands deshabilitado.");
    }

    public CommandManager getCommandManager()   { return commandManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
}
