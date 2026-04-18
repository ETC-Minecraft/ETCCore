package com.etcmc.etccore.command;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.manager.MuteManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MuteCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final ETCCore plugin;
    private final MuteManager muteManager;

    public MuteCommand(ETCCore plugin) {
        this.plugin = plugin;
        this.muteManager = plugin.getMuteManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase()) {
            case "mute"   -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            default       -> false;
        };
    }

    // ── /mute ────────────────────────────────────────────────────────────────

    private boolean handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("etccore.mute")) {
            send(sender, "&cNo tienes permiso.");
            return true;
        }
        if (args.length < 1) {
            send(sender, "&7Uso: &f/mute <jugador> [duración] [razón]");
            send(sender, "&7Duraciones: &f10s &8(seg) &f5m &8(min) &f2h &8(horas) &f1d &8(días) &fperm &8(permanente)");
            return true;
        }

        UUID uuid = resolveUUID(args[0]);
        if (uuid == null) {
            send(sender, "&cJugador '&f" + args[0] + "&c' no encontrado.");
            return true;
        }
        String displayName = resolveDisplayName(args[0], uuid);

        String defaultReason = plugin.getConfig().getString(
                "mute-default-reason", "Violación de las normas del servidor");

        long endTime;
        String reason;

        if (args.length >= 2 && isDuration(args[1])) {
            // /mute <player> <duration> [reason]
            long ms = parseDuration(args[1]);
            endTime = (ms == -1L) ? -1L : System.currentTimeMillis() + ms;
            reason  = (args.length >= 3)
                    ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                    : defaultReason;
        } else if (args.length >= 2) {
            // /mute <player> <reason...>  → sin duración = permanente
            endTime = -1L;
            reason  = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else {
            // /mute <player>  → permanente + razón por defecto
            endTime = -1L;
            reason  = defaultReason;
        }

        muteManager.mute(uuid, endTime, reason);

        // ── Notificar al silenciado si está online ────────────────────────────
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            String durText   = endTime == -1L
                    ? "Permanente"
                    : MuteManager.formatMillis(endTime - System.currentTimeMillis());
            String untilText = endTime == -1L
                    ? "hasta que un administrador decida lo contrario"
                    : "durante " + durText;
            send(target, "&c&lHas sido silenciado.");
            send(target, " &7Razón:    &f" + reason);
            send(target, " &7Duración: &f" + (endTime == -1L ? "Permanente" : durText));
            send(target, " &7Podrás hablar de nuevo &f" + untilText + "&7.");
        }

        // ── Confirmar al ejecutor ─────────────────────────────────────────────
        String durDisplay = endTime == -1L
                ? "permanentemente"
                : "durante " + MuteManager.formatMillis(endTime - System.currentTimeMillis());
        send(sender, "&a" + displayName + " &7ha sido silenciado &f" + durDisplay + "&7.");
        send(sender, "&7Razón: &f" + reason);
        return true;
    }

    // ── /unmute ───────────────────────────────────────────────────────────────

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("etccore.mute")) {
            send(sender, "&cNo tienes permiso.");
            return true;
        }
        if (args.length < 1) {
            send(sender, "&7Uso: &f/unmute <jugador>");
            return true;
        }

        UUID uuid = resolveUUID(args[0]);
        if (uuid == null) {
            send(sender, "&cJugador '&f" + args[0] + "&c' no encontrado.");
            return true;
        }
        String displayName = resolveDisplayName(args[0], uuid);

        if (!muteManager.isMuted(uuid)) {
            send(sender, "&e" + displayName + " &7no está silenciado actualmente.");
            return true;
        }

        muteManager.unmute(uuid);
        send(sender, "&a" + displayName + " &7ha sido dessilenciado.");

        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            send(target, "&aYa puedes volver a hablar en el chat.");
        }
        return true;
    }

    // ── Tab complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("mute")) {
            return List.of("30s", "5m", "1h", "12h", "1d", "7d", "perm");
        }
        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void send(CommandSender s, String msg) {
        s.sendMessage(LEGACY.deserialize(msg));
    }

    private static boolean isDuration(String s) {
        return s.matches("\\d+[smhd]")
                || s.equalsIgnoreCase("perm")
                || s.equalsIgnoreCase("permanent");
    }

    /** Devuelve milisegundos, o -1 para permanente. */
    private static long parseDuration(String s) {
        if (s.equalsIgnoreCase("perm") || s.equalsIgnoreCase("permanent")) return -1L;
        char unit   = s.charAt(s.length() - 1);
        long amount = Long.parseLong(s.substring(0, s.length() - 1));
        return amount * switch (unit) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default  -> 1_000L;
        };
    }

    @SuppressWarnings("deprecation")
    private static UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return (offline != null) ? offline.getUniqueId() : null;
    }

    @SuppressWarnings("deprecation")
    private static String resolveDisplayName(String fallback, UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return (offline.getName() != null) ? offline.getName() : fallback;
    }
}
