package com.etcmc.etccore.command;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.manager.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TeleportCommand implements CommandExecutor, TabCompleter {

    private final ETCCore plugin;

    public TeleportCommand(ETCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "home" -> handleHome(sender, args);
            case "sethome" -> handleSetHome(sender, label, args);
            case "delhome" -> handleDelHome(sender, args);
            case "publichome" -> handlePublicHome(sender, args);
            case "publichomelist" -> handlePublicHomeList(sender, args);
            case "warp" -> handleWarp(sender, args);
            case "setwarp" -> handleSetWarp(sender, label, args);
            case "delwarp" -> handleDelWarp(sender, args);
            case "lobby" -> handleNamedTeleport(sender, "lobby");
            case "setlobby" -> handleSetNamed(sender, "lobby");
            case "spawn" -> handleNamedTeleport(sender, "spawn");
            case "setspawn" -> handleSetNamed(sender, "spawn");
            case "back" -> handleBack(sender);
            case "reborn" -> handleReborn(sender, args);
            case "deathlist" -> handleDeathList(sender);
            case "rtp" -> handleRtp(sender);
            case "tpa" -> handleTpa(sender, args, TeleportManager.RequestType.TO_TARGET);
            case "tpahere" -> handleTpa(sender, args, TeleportManager.RequestType.TARGET_HERE);
            case "tpaccept" -> handleTpResponse(sender, args, true);
            case "tpdeny" -> handleTpResponse(sender, args, false);
            case "tpaall" -> handleTpaAll(sender);
            case "tp" -> handleTp(sender, args, false);
            case "tpo" -> handleTp(sender, args, true);
            case "tphere" -> handleTpHere(sender, args, false);
            case "tpohere" -> handleTpHere(sender, args, true);
            case "tpall" -> handleTpAll(sender, args);
            case "tpignore" -> handleTpIgnore(sender, args);
            case "tpoffline" -> handleTpOffline(sender, args);
            default -> false;
        };
    }

    private boolean handleHome(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            List<TeleportManager.HomeRecord> homes = plugin.getTeleportManager().listHomes(player.getUniqueId());
            if (homes.isEmpty()) {
                player.sendMessage("§eNo tienes homes. Usa §f/sethome <nombre>§e.");
                return true;
            }
            player.sendMessage("§6Homes: §f" + homes.stream()
                    .map(home -> home.name() + (home.isPublic() ? "§7(public)" : ""))
                    .reduce((a, b) -> a + "§8, §f" + b)
                    .orElse(""));
            return true;
        }
        Location location = plugin.getTeleportManager().getHome(player.getUniqueId(), args[0]);
        if (location == null) {
            player.sendMessage("§cEse home no existe.");
            return true;
        }
        teleport(player, location, "§aTeleportado a home §f" + args[0] + "§a.", TeleportManager.TeleportType.HOME, false);
        return true;
    }

    private boolean handleSetHome(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: §f/" + label + " <nombre>");
            return true;
        }
        if (!plugin.getTeleportManager().isValidName(args[0])) {
            player.sendMessage("§cNombre inválido. Usa solo letras, números, _ o - (máx. 24).");
            return true;
        }
        boolean requireExisting = label.equalsIgnoreCase("edithome");
        boolean ok = plugin.getTeleportManager().setHome(player, args[0], player.getLocation(), false, requireExisting);
        if (!ok) {
            player.sendMessage(requireExisting
                    ? "§cEse home no existe para editar."
                    : "§cNo se pudo guardar el home. Puede que hayas alcanzado el límite.");
            return true;
        }
        player.sendMessage((requireExisting ? "§aHome actualizado: §f" : "§aHome guardado: §f") + args[0]);
        return true;
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: §f/delhome <nombre>");
            return true;
        }
        if (!plugin.getTeleportManager().deleteHome(player.getUniqueId(), args[0])) {
            player.sendMessage("§cEse home no existe.");
            return true;
        }
        player.sendMessage("§aHome eliminado: §f" + args[0]);
        return true;
    }

    private boolean handlePublicHome(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§eUso: §f/publichome <jugador> <home> §7o §f/publichome <set|toggle|remove> <home>");
            return true;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                player.sendMessage("§eUso: §f/publichome set <home>");
                return true;
            }
            if (!plugin.getTeleportManager().isValidName(args[1])) {
                player.sendMessage("§cNombre inválido.");
                return true;
            }
            boolean ok = plugin.getTeleportManager().setHome(player, args[1], player.getLocation(), true, false);
            if (!ok) {
                player.sendMessage("§cNo se pudo guardar el public home. Puede que hayas alcanzado el límite.");
                return true;
            }
            player.sendMessage("§aPublic home guardado: §f" + args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("toggle")) {
            if (args.length < 2) {
                player.sendMessage("§eUso: §f/publichome toggle <home>");
                return true;
            }
            Boolean nowPublic = plugin.getTeleportManager().toggleHomeVisibility(player, args[1]);
            if (nowPublic == null) {
                player.sendMessage("§cNo se pudo cambiar la visibilidad de ese home. Debe existir y tener cupo si será público.");
                return true;
            }
            player.sendMessage(nowPublic ? "§aEse home ahora es público." : "§aEse home ahora es privado.");
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                player.sendMessage("§eUso: §f/publichome remove <home>");
                return true;
            }
            if (!plugin.getTeleportManager().setHomeVisibility(player, args[1], false)) {
                player.sendMessage("§cEse home no existe.");
                return true;
            }
            player.sendMessage("§aEse home ya no es público.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§eUso: §f/publichome <jugador> <home>");
            return true;
        }
        Location location = plugin.getTeleportManager().getPublicHome(args[0], args[1]);
        if (location == null) {
            player.sendMessage("§cNo se encontró ese public home.");
            return true;
        }
        teleport(player, location, "§aTeleportado a public home §f" + args[0] + ":" + args[1], TeleportManager.TeleportType.PUBLIC_HOME, false);
        return true;
    }

    private boolean handlePublicHomeList(CommandSender sender, String[] args) {
        List<String> homes = plugin.getTeleportManager().listPublicHomes(args.length > 0 ? args[0] : null);
        if (homes.isEmpty()) {
            sender.sendMessage("§eNo hay public homes registrados.");
            return true;
        }
        sender.sendMessage("§6Public homes: §f" + String.join("§8, §f", homes));
        return true;
    }

    private boolean handleWarp(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            List<String> warps = plugin.getTeleportManager().listWarps();
            if (warps.isEmpty()) {
                player.sendMessage("§eNo hay warps definidos.");
                return true;
            }
            player.sendMessage("§6Warps: §f" + String.join("§8, §f", warps));
            return true;
        }
        Location location = plugin.getTeleportManager().getWarp(args[0]);
        if (location == null) {
            player.sendMessage("§cEse warp no existe.");
            return true;
        }
        teleport(player, location, "§aTeleportado a warp §f" + args[0], TeleportManager.TeleportType.WARP, false);
        return true;
    }

    private boolean handleSetWarp(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: §f/" + label + " <nombre>");
            return true;
        }
        if (!plugin.getTeleportManager().isValidName(args[0])) {
            player.sendMessage("§cNombre inválido.");
            return true;
        }
        boolean requireExisting = label.equalsIgnoreCase("editwarp");
        boolean ok = plugin.getTeleportManager().setWarp(args[0], player.getLocation(), player.getName(), requireExisting);
        if (!ok) {
            player.sendMessage(requireExisting ? "§cEse warp no existe." : "§cNo se pudo guardar ese warp.");
            return true;
        }
        player.sendMessage((requireExisting ? "§aWarp actualizado: §f" : "§aWarp guardado: §f") + args[0]);
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§eUso: §f/delwarp <nombre>");
            return true;
        }
        if (!plugin.getTeleportManager().deleteWarp(args[0])) {
            sender.sendMessage("§cEse warp no existe.");
            return true;
        }
        sender.sendMessage("§aWarp eliminado: §f" + args[0]);
        return true;
    }

    private boolean handleNamedTeleport(CommandSender sender, String key) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        Location location = plugin.getTeleportManager().getNamedLocation(key);
        if (location == null) {
            if (key.equals("spawn")) {
                location = player.getWorld().getSpawnLocation();
            } else {
                player.sendMessage("§cNo hay " + key + " configurado todavía.");
                return true;
            }
        }
        teleport(player, location, "§aTeleportado a §f" + key + "§a.", key.equals("lobby") ? TeleportManager.TeleportType.LOBBY : TeleportManager.TeleportType.SPAWN, false);
        return true;
    }

    private boolean handleSetNamed(CommandSender sender, String key) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        plugin.getTeleportManager().setNamedLocation(key, player.getLocation());
        player.sendMessage("§a" + capitalize(key) + " establecido.");
        return true;
    }

    private boolean handleBack(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        Location location = plugin.getTeleportManager().getBackLocation(player.getUniqueId());
        if (location == null) {
            player.sendMessage("§cNo tienes ubicación anterior guardada.");
            return true;
        }
        teleport(player, location, "§aVolviste a tu última ubicación.", TeleportManager.TeleportType.BACK, false);
        return true;
    }

    private boolean handleReborn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        int index = 1;
        if (args.length > 0) {
            try {
                index = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cUsa un número de muerte válido. Ejemplo: §f/reborn 2");
                return true;
            }
        }
        TeleportManager.DeathRecord death = plugin.getTeleportManager().getDeath(player.getUniqueId(), index);
        if (death == null) {
            player.sendMessage("§cNo existe esa muerte guardada.");
            return true;
        }
        teleport(player, death.location(), "§aTeleportado a tu muerte #" + index + " §7(" + plugin.getTeleportManager().formatTimestamp(death.timestamp()) + "§7)", TeleportManager.TeleportType.REBORN, false);
        return true;
    }

    private boolean handleDeathList(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        List<TeleportManager.DeathRecord> deaths = plugin.getTeleportManager().listDeaths(player.getUniqueId());
        if (deaths.isEmpty()) {
            player.sendMessage("§eNo tienes muertes guardadas.");
            return true;
        }
        player.sendMessage("§6Muertes guardadas:");
        for (int i = 0; i < deaths.size(); i++) {
            TeleportManager.DeathRecord death = deaths.get(i);
            player.sendMessage("§e" + (i + 1) + ". §f" + plugin.getTeleportManager().formatLocationBrief(death.location())
                    + " §7- " + plugin.getTeleportManager().formatTimestamp(death.timestamp()));
        }
        player.sendMessage("§7Usa §f/reborn <n> §7para volver a una de ellas.");
        return true;
    }

    private boolean handleRtp(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        Location location = plugin.getTeleportManager().findRandomTeleportLocation(player.getWorld());
        if (location == null) {
            player.sendMessage("§cNo se encontró una ubicación segura para RTP.");
            return true;
        }
        teleport(player, location, "§aRTP completado: §f" + plugin.getTeleportManager().formatLocationBrief(location), TeleportManager.TeleportType.RTP, false);
        return true;
    }

    private boolean handleTpa(CommandSender sender, String[] args, TeleportManager.RequestType type) {
        Player requester = requirePlayer(sender);
        if (requester == null) {
            return true;
        }
        if (args.length < 1) {
            requester.sendMessage("§eUso: §f/" + (type == TeleportManager.RequestType.TO_TARGET ? "tpa" : "tpahere") + " <jugador>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            requester.sendMessage("§cEse jugador no está online.");
            return true;
        }
        if (target.equals(requester)) {
            requester.sendMessage("§cNo puedes enviarte una solicitud a ti mismo.");
            return true;
        }
        TeleportManager.RequestStatus status = plugin.getTeleportManager().createRequest(requester, target, type);
        if (status == TeleportManager.RequestStatus.IGNORED) {
            requester.sendMessage("§cEse jugador está ignorando tus solicitudes de TP.");
            return true;
        }
        requester.sendMessage("§aSolicitud enviada a §f" + target.getName());
        target.sendMessage(type == TeleportManager.RequestType.TO_TARGET
                ? "§e" + requester.getName() + " quiere ir hacia ti. Usa §f/tpaccept " + requester.getName() + " §eo §f/tpdeny " + requester.getName()
                : "§e" + requester.getName() + " quiere que vayas hacia él. Usa §f/tpaccept " + requester.getName() + " §eo §f/tpdeny " + requester.getName());
        return true;
    }

    private boolean handleTpResponse(CommandSender sender, String[] args, boolean accept) {
        Player target = requirePlayer(sender);
        if (target == null) {
            return true;
        }
        Player requester = null;
        if (args.length > 0) {
            requester = Bukkit.getPlayerExact(args[0]);
            if (requester == null) {
                target.sendMessage("§cEse jugador no está online.");
                return true;
            }
        }
        TeleportManager.TeleportRequest request = plugin.getTeleportManager()
                .consumeRequest(target.getUniqueId(), requester == null ? null : requester.getUniqueId());
        if (request == null) {
            target.sendMessage("§cNo tienes solicitudes pendientes.");
            return true;
        }
        Player source = Bukkit.getPlayer(request.requesterId());
        if (source == null) {
            target.sendMessage("§cEl jugador que envió la solicitud ya no está online.");
            return true;
        }
        if (!accept) {
            target.sendMessage("§eSolicitud denegada.");
            source.sendMessage("§c" + target.getName() + " rechazó tu solicitud.");
            return true;
        }

        if (request.type() == TeleportManager.RequestType.TO_TARGET) {
            teleport(source, target.getLocation(), "§aSolicitud aceptada por §f" + target.getName(), TeleportManager.TeleportType.TPA, false);
            target.sendMessage("§aAceptaste la solicitud de §f" + source.getName());
        } else {
            teleport(target, source.getLocation(), "§aTeletransportado hacia §f" + source.getName(), TeleportManager.TeleportType.TPA, false);
            source.sendMessage("§a" + target.getName() + " aceptó ir hacia ti.");
        }
        return true;
    }

    private boolean handleTpaAll(CommandSender sender) {
        Player requester = requirePlayer(sender);
        if (requester == null) {
            return true;
        }
        int count = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(requester)) {
                continue;
            }
            if (plugin.getTeleportManager().createRequest(requester, target, TeleportManager.RequestType.TARGET_HERE)
                    == TeleportManager.RequestStatus.CREATED) {
                target.sendMessage("§e" + requester.getName() + " quiere que vayas hacia él. Usa §f/tpaccept " + requester.getName());
                count++;
            }
        }
        requester.sendMessage(count == 0
                ? "§eNo se pudo enviar ninguna solicitud."
                : "§aSolicitudes enviadas a §f" + count + " §ajugador(es).");
        return true;
    }

    private boolean handleTp(CommandSender sender, String[] args, boolean bypass) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: §f/" + (bypass ? "tpo" : "tp") + " <jugador> §7o §f/" + (bypass ? "tpo" : "tp") + " <jugador1> <jugador2>");
            return true;
        }
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cEse jugador no está online.");
                return true;
            }
            teleport(player, target.getLocation(), "§aTeleportado a §f" + target.getName(), TeleportManager.TeleportType.TP_ADMIN, bypass);
            return true;
        }
        Player source = Bukkit.getPlayerExact(args[0]);
        Player target = Bukkit.getPlayerExact(args[1]);
        if (source == null || target == null) {
            player.sendMessage("§cAmbos jugadores deben estar online.");
            return true;
        }
        teleport(source, target.getLocation(), "§aHas sido teletransportado a §f" + target.getName() + " §apor " + player.getName(), TeleportManager.TeleportType.TP_ADMIN, true);
        player.sendMessage("§aTeleportado §f" + source.getName() + " §aa §f" + target.getName());
        return true;
    }

    private boolean handleTpHere(CommandSender sender, String[] args, boolean bypass) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: §f/" + (bypass ? "tpohere" : "tphere") + " <jugador>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cEse jugador no está online.");
            return true;
        }
        teleport(target, player.getLocation(), "§aHas sido teletransportado hacia §f" + player.getName(), TeleportManager.TeleportType.TP_ADMIN, true);
        player.sendMessage("§aTrajiste a §f" + target.getName());
        return true;
    }

    private boolean handleTpAll(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        Location targetLocation = player.getLocation();
        String targetName = player.getName();
        if (args.length > 0) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage("§cEse jugador no está online.");
                return true;
            }
            targetLocation = target.getLocation();
            targetName = target.getName();
        }
        int moved = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player) && args.length == 0) {
                continue;
            }
            teleport(online, targetLocation, "§aHas sido teletransportado por §f" + player.getName(), TeleportManager.TeleportType.TP_ADMIN, true);
            moved++;
        }
        player.sendMessage("§aTeleportados §f" + moved + " §ajugador(es) a §f" + targetName);
        return true;
    }

    private boolean handleTpIgnore(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: §f/tpignore <jugador>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cEse jugador no está online.");
            return true;
        }
        boolean nowIgnoring = plugin.getTeleportManager().toggleIgnore(player, target);
        player.sendMessage(nowIgnoring
                ? "§aAhora ignoras las solicitudes de §f" + target.getName()
                : "§aYa no ignoras las solicitudes de §f" + target.getName());
        return true;
    }

    private boolean handleTpOffline(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: §f/tpoffline <jugador>");
            return true;
        }
        Location location = plugin.getTeleportManager().getOfflineLocation(args[0]);
        if (location == null) {
            player.sendMessage("§cNo hay ubicación offline guardada para ese jugador.");
            return true;
        }
        teleport(player, location, "§aTeleportado a la última ubicación offline de §f" + args[0], TeleportManager.TeleportType.TP_ADMIN, true);
        return true;
    }

    private void teleport(Player player,
                          Location location,
                          String successMessage,
                          TeleportManager.TeleportType type,
                          boolean bypassDelaysAndCooldowns) {
        plugin.getTeleportManager().executeTeleport(player, location, successMessage, type, bypassDelaysAndCooldowns);
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("§cEste comando solo puede usarlo un jugador.");
        return null;
    }

    private String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "home", "sethome", "delhome" -> completeOwnHomes(sender, args, name.equals("sethome"));
            case "publichome" -> completePublicHome(args);
            case "publichomelist" -> args.length == 1 ? onlineNames() : List.of();
            case "warp", "setwarp", "delwarp" -> completeWarps(args, name.equals("setwarp"));
            case "tpa", "tpahere", "tpignore", "tp", "tpo", "tphere", "tpohere", "tpall", "tpaall", "tpaccept", "tpdeny" -> onlineNames(args);
            case "tpoffline" -> List.of();
            case "reborn" -> completeDeathIndexes(sender, args);
            default -> List.of();
        };
    }

    private List<String> completeOwnHomes(CommandSender sender, String[] args, boolean createOnly) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1 && !createOnly) {
            return plugin.getTeleportManager().listHomes(player.getUniqueId()).stream()
                    .map(TeleportManager.HomeRecord::name)
                    .filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private List<String> completePublicHome(String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(onlineNames());
            values.add("set");
            values.add("toggle");
            values.add("remove");
            return filter(values, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("toggle") || args[0].equalsIgnoreCase("remove"))) {
            return List.of();
        }
        return List.of();
    }

    private List<String> completeWarps(String[] args, boolean createOnly) {
        if (args.length == 1 && !createOnly) {
            return filter(plugin.getTeleportManager().listWarps(), args[0]);
        }
        return List.of();
    }

    private List<String> completeDeathIndexes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int count = plugin.getTeleportManager().listDeaths(player.getUniqueId()).size();
        for (int i = 1; i <= count; i++) {
            values.add(String.valueOf(i));
        }
        return filter(values, args[0]);
    }

    private List<String> onlineNames(String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return filter(onlineNames(), args[0]);
    }

    private List<String> onlineNames() {
        List<String> values = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            values.add(player.getName());
        }
        Collections.sort(values);
        return values;
    }

    private List<String> filter(Collection<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .toList();
    }
}