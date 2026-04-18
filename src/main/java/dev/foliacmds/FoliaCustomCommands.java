package dev.foliacmds;

import dev.foliacmds.command.EnderChestCommand;
import dev.foliacmds.command.InvSeeCommand;
import dev.foliacmds.command.MuteCommand;
import dev.foliacmds.command.ReloadCommand;
import dev.foliacmds.command.TeleportCommand;
import dev.foliacmds.command.VanishCommand;
import dev.foliacmds.manager.VanishManager;
import dev.foliacmds.listener.BuildProtectionListener;
import dev.foliacmds.listener.ChatProtectionListener;
import dev.foliacmds.listener.CommandBlockListener;
import dev.foliacmds.listener.JoinCommandListener;
import dev.foliacmds.listener.PlaytimeListener;
import dev.foliacmds.listener.RespawnListener;
import dev.foliacmds.listener.TeleportListener;
import dev.foliacmds.listener.VanishListener;
import dev.foliacmds.manager.ChatInputManager;
import dev.foliacmds.manager.CommandBlockManager;
import dev.foliacmds.manager.CommandLogger;
import dev.foliacmds.manager.CommandManager;
import dev.foliacmds.manager.CooldownManager;
import dev.foliacmds.manager.FeatureConfigManager;
import dev.foliacmds.manager.FileWatcher;
import dev.foliacmds.manager.MenuManager;
import dev.foliacmds.manager.MuteManager;
import dev.foliacmds.manager.PlaytimeManager;
import dev.foliacmds.manager.PlayerDataManager;
import dev.foliacmds.manager.ScheduledTaskManager;
import dev.foliacmds.manager.ETCCorePlaceholders;
import dev.foliacmds.manager.TeleportManager;
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
    private FeatureConfigManager     featureConfigManager;
    private TeleportManager          teleportManager;
    private PlaytimeManager          playtimeManager;
    // ── New managers ────────────────────────────────────────────────────────
    private VaultManager             vaultManager;
    private UpdateChecker            updateChecker;
    private CommandLogger            commandLogger;
    private ScheduledTaskManager     scheduledTaskManager;
    private VanishManager            vanishManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultCommands();
        featureConfigManager = new FeatureConfigManager(this);

        // ── Core managers ────────────────────────────────────────────────────
        vanishManager        = new VanishManager(this);
        vanishManager.startSyncTask();
        cooldownManager      = new CooldownManager();
        playerDataManager    = new PlayerDataManager(this);
        teleportManager      = new TeleportManager(this);
        playtimeManager      = new PlaytimeManager(this);
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
        playtimeManager.start();

        // ── Listeners ────────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        chatProtectionListener = new ChatProtectionListener(this);
        getServer().getPluginManager().registerEvents(chatProtectionListener, this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);

        if (getConfig().getBoolean("build-protection", true)) {
            getServer().getPluginManager().registerEvents(new BuildProtectionListener(this), this);
        }

        getServer().getPluginManager().registerEvents(new JoinCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);
        getServer().getPluginManager().registerEvents(new PlaytimeListener(this), this);

        // Vanish: ocultar del MOTD, TAB y manejar eventos de join/quit
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);

        var vanishCmd = getCommand("vanish");
        if (vanishCmd != null) {
            VanishCommand vc = new VanishCommand(this);
            vanishCmd.setExecutor(vc);
            vanishCmd.setTabCompleter(vc);
        }

        // ── PlaceholderAPI expansion ─────────────────────────────────────────
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ETCCorePlaceholders(this).register();
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

        var fccCmd = getCommand("etccore");
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

        TeleportCommand teleportCommand = new TeleportCommand(this);
        for (String commandName : new String[]{
                "home", "sethome", "delhome", "publichome", "publichomelist",
                "warp", "setwarp", "delwarp", "lobby", "setlobby",
                "spawn", "setspawn", "back", "reborn", "deathlist",
                "rtp", "tp", "tpa", "tpahere", "tpaall",
                "tpaccept", "tpdeny", "tpall", "tphere", "tpignore",
                "tpo", "tpoffline", "tpohere"
        }) {
            var registered = getCommand(commandName);
            if (registered != null) {
                registered.setExecutor(teleportCommand);
                registered.setTabCompleter(teleportCommand);
            }
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
        if (playtimeManager != null) playtimeManager.shutdown();
        if (fileWatcher != null) fileWatcher.stop();
        if (watcherThread != null) watcherThread.interrupt();
        getLogger().info("ETCCore deshabilitado.");
    }

    public CommandManager          getCommandManager()          { return commandManager; }
    public CooldownManager         getCooldownManager()         { return cooldownManager; }
    public FeatureConfigManager    getFeatureConfigManager()    { return featureConfigManager; }
    public PlayerDataManager       getPlayerDataManager()       { return playerDataManager; }
    public TeleportManager         getTeleportManager()         { return teleportManager; }
    public PlaytimeManager         getPlaytimeManager()         { return playtimeManager; }
    public MenuManager             getMenuManager()             { return menuManager; }
    public ChatInputManager        getChatInputManager()        { return chatInputManager; }
    public MuteManager             getMuteManager()             { return muteManager; }
    public CommandBlockManager     getCommandBlockManager()     { return commandBlockManager; }
    public ChatProtectionListener  getChatProtectionListener()  { return chatProtectionListener; }
    public VaultManager            getVaultManager()            { return vaultManager; }
    public CommandLogger           getCommandLogger()           { return commandLogger; }
    public ScheduledTaskManager    getScheduledTaskManager()    { return scheduledTaskManager; }
    public VanishManager           getVanishManager()           { return vanishManager; }
}

