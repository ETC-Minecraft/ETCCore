package dev.foliacmds;

import dev.foliacmds.command.EnderChestCommand;
import dev.foliacmds.command.InvSeeCommand;
import dev.foliacmds.command.ReloadCommand;
import dev.foliacmds.manager.BuildProtectionListener;
import dev.foliacmds.manager.ChatInputManager;
import dev.foliacmds.manager.CommandManager;
import dev.foliacmds.manager.CooldownManager;
import dev.foliacmds.manager.FileWatcher;
import dev.foliacmds.manager.MenuManager;
import dev.foliacmds.manager.PlayerDataManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class FoliaCustomCommands extends JavaPlugin {

    private CommandManager commandManager;
    private CooldownManager cooldownManager;
    private PlayerDataManager playerDataManager;
    private MenuManager menuManager;
    private ChatInputManager chatInputManager;
    private FileWatcher fileWatcher;
    private Thread watcherThread;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultCommands();

        cooldownManager    = new CooldownManager();
        playerDataManager  = new PlayerDataManager(this);
        chatInputManager   = new ChatInputManager(this);
        menuManager        = new MenuManager(this);
        commandManager     = new CommandManager(this);

        menuManager.loadMenus();
        commandManager.loadCommands();

        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);

        // Protección de construcción (se puede desactivar con build-protection: false)
        if (getConfig().getBoolean("build-protection", true)) {
            getServer().getPluginManager().registerEvents(new BuildProtectionListener(this), this);
        }

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
            ReloadCommand rc = new ReloadCommand(this);
            fccCmd.setExecutor(rc);
            fccCmd.setTabCompleter(rc);
        }

        var ecCmd = getCommand("enderchest");
        if (ecCmd != null) {
            EnderChestCommand ec = new EnderChestCommand(this);
            ecCmd.setExecutor(ec);
            ecCmd.setTabCompleter(ec);
            getServer().getPluginManager().registerEvents(ec, this);
        }

        var isCmd = getCommand("invsee");
        if (isCmd != null) {
            InvSeeCommand is = new InvSeeCommand(this);
            isCmd.setExecutor(is);
            isCmd.setTabCompleter(is);
            getServer().getPluginManager().registerEvents(is, this);
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

    public CommandManager    getCommandManager()    { return commandManager; }
    public CooldownManager   getCooldownManager()   { return cooldownManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public MenuManager       getMenuManager()       { return menuManager; }
    public ChatInputManager  getChatInputManager()  { return chatInputManager; }
}

