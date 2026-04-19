package com.etcmc.etccore.command;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.menu.HomesMenu;
import com.etcmc.etccore.menu.MainMenu;
import com.etcmc.etccore.menu.PocketWorldsMenu;
import com.etcmc.etccore.menu.StatsMenu;
import com.etcmc.etccore.menu.TPAMenu;
import com.etcmc.etccore.menu.TopMenu;
import com.etcmc.etccore.menu.WarpsMenu;
import com.etcmc.etccore.menu.WorldsMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /menu [submenu]
 *
 * Sin argumentos abre el menú principal. Con argumento abre directamente
 * un submenú concreto (mundos, pocketworlds, homes, warps, tpa, stats, top).
 */
public class MenuCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "mundos", "pocketworlds", "homes", "warps", "tpa", "stats", "top"
    );

    private final ETCCore plugin;

    public MenuCommand(ETCCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSólo en el juego.");
            return true;
        }
        if (!p.hasPermission("etccore.menu")) {
            p.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            new MainMenu(plugin, p).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "mundos", "worlds"           -> new WorldsMenu(plugin, p, 0).open();
            case "pocketworlds", "pw"         -> new PocketWorldsMenu(plugin, p, 0).open();
            case "homes", "home"              -> new HomesMenu(plugin, p, 0).open();
            case "warps", "warp"              -> new WarpsMenu(plugin, p, 0).open();
            case "tpa", "requests"            -> new TPAMenu(plugin, p).open();
            case "stats", "estadisticas"      -> new StatsMenu(plugin, p).open();
            case "top"                        -> new TopMenu(plugin, p, 0).open();
            default                           -> new MainMenu(plugin, p).open();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase();
        return SUBS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
    }
}
