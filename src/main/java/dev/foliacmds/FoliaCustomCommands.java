package dev.foliacmds;

import dev.foliacmds.command.EnderChestCommand;
import dev.foliacmds.command.InvSeeCommand;
import dev.foliacmds.command.MuteCommand;
import dev.foliacmds.command.ReloadCommand;
import dev.foliacmds.listener.BuildProtectionListener;
import dev.foliacmds.listener.ChatProtectionListener;
import dev.foliacmds.listener.CommandBlockListener;
import dev.foliacmds.manager.ChatInputManager;
import dev.foliacmds.manager.CommandBlockManager;
import dev.foliacmds.manager.CommandLogger;
import dev.foliacmds.manager.CommandManager;
import dev.foliacmds.manager.CooldownManager;
import dev.foliacmds.manager.FileWatcher;
import dev.foliacmds.manager.MenuManager;
import dev.foliacmds.manager.MuteManager;
import dev.foliacmds.manager.PlayerDataManager;
import dev.foliacmds.manager.ScheduledTaskManager;
import dev.foliacmds.manager.UpdateChecker;
import dev.foliacmds.manager.VaultManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class FoliaCustomCommands extends JavaPlugin {

    private CommandManager           commandManager;
    private CooldownManager          cooldownManager;
    private PlayerDataManager        playerDataManager;
    private MenuManager              menuManager;
    private ChatInputManager         chatInputManager;
    private MuteManager              muteManager;
    private CommandBlockManager      commandBlockManager;
    private ChatProtectionListener   chatProtectionListener;
    private FileWatcher              fileWatcher;
    private Thread                   watcherThread;
    // ── New managers ────────────────────────────────────────────────────────
    private VaultManager             vaultManager;
    private UpdateChecker            updateChecker;
    private CommandLogger            commandLogger;
    private ScheduledTaskManager     scheduledTaskManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultCommands();

        // ── Core managers ────────────────────────────────────────────────────
        cooldownManager      = new CooldownManager();
        playerDataManager    = new PlayerDataManager(this);
        chatInputManager     = new ChatInputManager(this);
        muteManager          = new MuteManager(this);
        commandBlockManager  = new CommandBlockManager(this);
        menuManager          = new MenuManager(this);
        commandManager       = new CommandManager(this);

        // ── New feature managers ─────────────────────────────────────────────
        vaultManager         = new VaultManager(this);
        commandLogger        = new CommandLogger(this);
        scheduledTaskManager = new ScheduledTaskManager(this);

        menuManager.loadMenus();
        commandManager.loadCommands();
        scheduledTaskManager.load();

        // ── Listeners ────────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        chatProtectionListener = new ChatProtectionListener(this);
        getServer().getPluginManager().registerEvents(chatProtectionListener, this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);

        if (getConfig().getBoolean("build-protection", true)) {
            getServer().getPluginManager().registerEvents(new BuildProtectionListener(this), this);
        }

        // ── Update checker ───────────────────────────────────────────────────
        if (getConfig().getBoolean("update-checker", true)) {
            updateChecker = new UpdateChecker(this);
            getServer().getPluginManager().registerEvents(updateChecker, this);
            updateChecker.check();
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

        var muteCmd = getCommand("mute");
        if (muteCmd != null) {
            MuteCommand mc = new MuteCommand(this);
            muteCmd.setExecutor(mc);
            muteCmd.setTabCompleter(mc);
        }
        var unmuteCmd = getCommand("unmute");
        if (unmuteCmd != null) {
            MuteCommand mc = new MuteCommand(this);
            unmuteCmd.setExecutor(mc);
            unmuteCmd.setTabCompleter(mc);
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

        getLogger().info("ETCCore habilitado — "
                + commandManager.getCommandCount() + " comando(s) cargado(s).");
    }

    private void saveDefaultCommands() {
        File commandsDir = new File(getDataFolder(), "commands");
        if (!commandsDir.exists()) {
            commandsDir.mkdirs();
            saveResource("commands/survival.yml", false);
            saveResource("commands/inicio.yml", false);
        }

        // Create scheduled folder with example if it doesn't exist
        File scheduledDir = new File(getDataFolder(), "commands/scheduled");
        if (!scheduledDir.exists()) {
            scheduledDir.mkdirs();
            saveResource("commands/scheduled/reset-diario.yml", false);
        }

        File menusDir = new File(getDataFolder(), "menus");
        if (!menusDir.exists()) {
            menusDir.mkdirs();
            saveResource("menus/panel.yml", false);
            saveResource("menus/tienda.yml", false);
        }

        File playerDataDir = new File(getDataFolder(), "playerdata");
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs();
            saveResource("playerdata/LEEME.txt", false);
        }
    }

    @Override
    public void onDisable() {
        if (scheduledTaskManager != null) scheduledTaskManager.shutdown();
        if (fileWatcher != null) fileWatcher.stop();
        if (watcherThread != null) watcherThread.interrupt();
        getLogger().info("ETCCore deshabilitado.");
    }

    public CommandManager          getCommandManager()          { return commandManager; }
    public CooldownManager         getCooldownManager()         { return cooldownManager; }
    public PlayerDataManager       getPlayerDataManager()       { return playerDataManager; }
    public MenuManager             getMenuManager()             { return menuManager; }
    public ChatInputManager        getChatInputManager()        { return chatInputManager; }
    public MuteManager             getMuteManager()             { return muteManager; }
    public CommandBlockManager     getCommandBlockManager()     { return commandBlockManager; }
    public ChatProtectionListener  getChatProtectionListener()  { return chatProtectionListener; }
    public VaultManager            getVaultManager()            { return vaultManager; }
    public CommandLogger           getCommandLogger()           { return commandLogger; }
    public ScheduledTaskManager    getScheduledTaskManager()    { return scheduledTaskManager; }
}

