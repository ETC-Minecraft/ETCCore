package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;
import dev.foliacmds.command.CustomCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

public class CommandManager {

    private final FoliaCustomCommands plugin;
    private final Map<String, CustomCommand> registeredCommands = new HashMap<>();
    // Nodos de permiso registrados dinámicamente (para limpiarlos al recargar)
    private final Set<String> dynPermissions = new HashSet<>();

    public CommandManager(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Carga inicial
    // -------------------------------------------------------------------------
    public void loadCommands() {
        CommandMap commandMap = getCommandMap();
        if (commandMap == null) return;

        Map<String, ConfigurationSection> commands = scanAllCommands();
        if (commands.isEmpty()) {
            plugin.getLogger().warning("No se encontraron comandos en commands/");
            return;
        }

        boolean verbose = plugin.getConfig().getBoolean("verbose-outputs", false);
        for (Map.Entry<String, ConfigurationSection> entry : commands.entrySet()) {
            String name = entry.getKey();
            CustomCommand cmd = buildCommand(name, entry.getValue());
            commandMap.register(plugin.getName().toLowerCase(), cmd);
            registeredCommands.put(name, cmd);
            registerDynPermission("fccmds.commands." + name);
            if (verbose) plugin.getLogger().info("Comando registrado: /" + name);
        }
    }

    // -------------------------------------------------------------------------
    // Recarga
    // -------------------------------------------------------------------------
    public void reload() {
        plugin.reloadConfig();
        Map<String, ConfigurationSection> current = scanAllCommands();
        CommandMap commandMap = getCommandMap();

        // Actualizar o registrar
        for (Map.Entry<String, ConfigurationSection> entry : current.entrySet()) {
            String name = entry.getKey();
            ConfigurationSection sec = entry.getValue();
            if (registeredCommands.containsKey(name)) {
                registeredCommands.get(name).applySection(sec);
                registeredCommands.get(name).setDisabled(false);
            } else if (commandMap != null) {
                CustomCommand cmd = buildCommand(name, sec);
                commandMap.register(plugin.getName().toLowerCase(), cmd);
                registeredCommands.put(name, cmd);
                registerDynPermission("fccmds.commands." + name);
                plugin.getLogger().info("Nuevo comando registrado: /" + name);
            }
        }

        // Deshabilitar comandos eliminados
        for (Map.Entry<String, CustomCommand> entry : registeredCommands.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                entry.getValue().setDisabled(true);
                unregisterDynPermission("fccmds.commands." + entry.getKey());
                plugin.getLogger().info("Comando deshabilitado: /" + entry.getKey());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Escaneo de archivos — soporta dos formatos:
    //   • Formato simple:   sin sección "commands:", el nombre del archivo
    //                       es el nombre del comando.
    //   • Formato múltiple: sección "commands:" con varios sub-comandos.
    // -------------------------------------------------------------------------
    private Map<String, ConfigurationSection> scanAllCommands() {
        Map<String, ConfigurationSection> result = new LinkedHashMap<>();
        File[] files = listCommandFiles();
        if (files == null) return result;

        for (File file : files) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            if (cfg.isConfigurationSection("commands")) {
                // Formato múltiple: cada clave bajo "commands:" es un comando
                ConfigurationSection cmds = cfg.getConfigurationSection("commands");
                for (String name : cmds.getKeys(false)) {
                    ConfigurationSection sec = cmds.getConfigurationSection(name);
                    if (sec != null) result.put(name.toLowerCase(), sec);
                }
            } else {
                // Formato simple: el nombre del archivo es el comando
                result.put(fileToName(file), cfg);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private CustomCommand buildCommand(String name, ConfigurationSection sec) {
        return new CustomCommand(plugin, name, sec);
    }

    private File[] listCommandFiles() {
        File dir = new File(plugin.getDataFolder(), "commands");
        if (!dir.exists()) dir.mkdirs();
        return dir.listFiles((d, n) -> n.endsWith(".yml"));
    }

    private String fileToName(File file) {
        return file.getName().replace(".yml", "").toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Gestión de permisos dinámicos (para LuckPerms y similares)
    // ▸ PermissionDefault.TRUE  → todos los jugadores tienen el nodo salvo negación explícita
    // ▸ Admins pueden hacer: /lp group default permission set fccmds.commands.kit false
    // -------------------------------------------------------------------------
    private void registerDynPermission(String node) {
        if (dynPermissions.contains(node)) return; // ya registrado
        try {
            Bukkit.getPluginManager().addPermission(
                    new Permission(node, PermissionDefault.TRUE));
            dynPermissions.add(node);
        } catch (IllegalArgumentException ignored) {
            // otro plugin ya registró este nodo — lo añadimos al set igualmente
            dynPermissions.add(node);
        }
    }

    private void unregisterDynPermission(String node) {
        Bukkit.getPluginManager().removePermission(node);
        dynPermissions.remove(node);
    }

    private CommandMap getCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().severe("No se pudo obtener el CommandMap: " + e.getMessage());
            return null;
        }
    }

    public int getCommandCount() {
        return registeredCommands.size();
    }

    /**
     * Permite a otros sistemas (MenuManager, etc.) ejecutar una acción
     * usando el motor de CustomCommand sin necesidad de un contexto de comando.
     */
    public void dispatchAction(org.bukkit.entity.Player player, String action, String[] args) {
        // Crear un CustomCommand temporal solo para usar su processAction vía el motor
        // Delegamos a un método estático expuesto por CustomCommand
        dev.foliacmds.command.CustomCommand.fireAction(plugin, player, action, args);
    }
}
