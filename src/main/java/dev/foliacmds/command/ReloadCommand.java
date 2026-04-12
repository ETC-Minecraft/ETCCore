package dev.foliacmds.command;

import dev.foliacmds.FoliaCustomCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final FoliaCustomCommands plugin;

    public ReloadCommand(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§eUso: §f/fcc reload");
            return true;
        }

        // Ejecutar en el hilo global para no bloquear ninguna región
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            plugin.getCommandManager().reload();
            sender.sendMessage("§a[FoliaCustomCommands] §fRecargado correctamente. §7("
                    + plugin.getCommandManager().getCommandCount() + " comando(s))");
        });

        return true;
    }
}
