package dev.foliacmds.command;

import dev.foliacmds.FoliaCustomCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ReloadCommand implements CommandExecutor, TabCompleter {

    private final FoliaCustomCommands plugin;

    public ReloadCommand(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUso: §f/etccore reload §7| §f/etccore onlinemode [true|false]");
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ── /etccore reload ─────────────────────────────────────────────
            case "reload" -> {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    plugin.reloadConfig();
                    plugin.getCommandManager().reload();
                    plugin.getMenuManager().loadMenus();
                    plugin.getCommandBlockManager().reload();
                    plugin.getChatProtectionListener().reload();
                    plugin.getCommandLogger().reload();
                    plugin.getScheduledTaskManager().reload();
                    sender.sendMessage("§a[ETCCore] §fRecargado. §7("
                            + plugin.getCommandManager().getCommandCount() + " cmd, "
                            + plugin.getMenuManager().getMenuNames().size() + " menús)");
                });
            }

            // ── /etccore onlinemode ─────────────────────────────────────────
            case "onlinemode" -> {
                if (!sender.hasPermission("etccore.admin")) {
                    sender.sendMessage("§cNo tienes permiso.");
                    return true;
                }

                if (args.length < 2) {
                    // Solo consultar el estado actual
                    boolean current = Bukkit.getServer().getOnlineMode();
                    sender.sendMessage("§8[§bFCC§8] Online-mode actual: "
                            + (current ? "§aON (premium)": "§cOFF (sin autenticación)"));
                    sender.sendMessage("§7Uso: §f/etccore onlinemode <true|false>");
                    return true;
                }

                boolean target;
                if (args[1].equalsIgnoreCase("true"))  target = true;
                else if (args[1].equalsIgnoreCase("false")) target = false;
                else {
                    sender.sendMessage("§cValor inválido. Usa §ftrue §co §ffalse§c.");
                    return true;
                }

                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    try {
                        setOnlineMode(target);
                        String estado = target ? "§aON §7(solo cuentas premium pueden entrar)"
                                               : "§cOFF §7(cualquier cliente puede entrar)";
                        sender.sendMessage("§8[§bFCC§8] Online-mode cambiado a: " + estado);
                        sender.sendMessage("§7Afecta solo a nuevas conexiones. Usa §f/etccore onlinemode " + !target + "§7 para revertir.");
                        plugin.getLogger().warning("Online-mode cambiado a " + target + " por " + sender.getName());
                    } catch (Exception e) {
                        sender.sendMessage("§cError al cambiar online-mode: " + e.getMessage());
                        plugin.getLogger().severe("Error al cambiar online-mode: " + e);
                    }
                });
            }

            default -> sender.sendMessage("§eUso: §f/etccore reload §7| §f/etccore onlinemode [true|false]");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("etccore.admin")) return List.of();
        if (args.length == 1) return List.of("reload", "onlinemode");
        if (args.length == 2 && args[0].equalsIgnoreCase("onlinemode")) return List.of("true", "false");
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Cambia el campo onlineMode en MinecraftServer vía reflexión.
    // Busca el campo subiendo por la jerarquía de clases para no depender
    // de nombres internos de Paper que pueden cambiar entre versiones.
    // -------------------------------------------------------------------------
    private void setOnlineMode(boolean value) throws Exception {
        // CraftServer -> getServer() -> DedicatedServer (extiende MinecraftServer)
        Method getServer = Bukkit.getServer().getClass().getMethod("getServer");
        Object nmsServer = getServer.invoke(Bukkit.getServer());

        // Buscar el campo 'onlineMode' subiendo por la jerarquía
        Field field = findField(nmsServer.getClass(), "onlineMode");
        field.setAccessible(true);
        field.set(nmsServer, value);
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException("Campo '" + name + "' no encontrado en la jerarquía de " + clazz.getName());
    }
}
