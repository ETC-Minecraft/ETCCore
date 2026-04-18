package com.etcmc.etccore.command;

import com.etcmc.etccore.ETCCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final ETCCore plugin;

    public VanishCommand(ETCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("etccore.vanish")) {
            sender.sendMessage(Component.text("Sin permiso.", NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text(
                        "Jugador no encontrado: " + args[0], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Uso: /vanish <jugador>", NamedTextColor.RED));
            return true;
        }

        boolean nowVanished = plugin.getVanishManager().toggle(target);

        // Notificar al ejecutor si es diferente al objetivo
        if (!target.equals(sender)) {
            sender.sendMessage(Component.text()
                    .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(
                            nowVanished ? " ahora está en vanish." : " ya no está en vanish.",
                            nowVanished ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .build());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (!sender.hasPermission("etccore.vanish")) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
