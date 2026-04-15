package dev.foliacmds.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.foliacmds.FoliaCustomCommands;
import dev.foliacmds.manager.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener centralizado para el sistema de vanish de ETCCore.
 * Maneja: TAB list, MOTD ping, eventos de join/quit.
 */
public class VanishListener implements Listener {

    private final FoliaCustomCommands plugin;

    public VanishListener(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    /**
     * Al conectarse un jugador:
     * 1. Oculta los jugadores vanished del nuevo jugador (si no tiene permiso para verlos).
     * 2. Si el propio usuario que se conecta estaba en vanish (set cargado), re-aplica ocultado.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        VanishManager vm = plugin.getVanishManager();
        vm.applyToJoiner(joiner);
        if (vm.isVanishedByETC(joiner.getUniqueId())) {
            vm.applyOnReJoin(joiner);
        }
    }

    /** Al desconectarse, limpiar su entrada del set (el vanish no persiste entre sesiones). */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getVanishManager().onDisconnect(event.getPlayer());
    }

    /**
     * Oculta jugadores en vanish del MOTD:
     * — Reduce el contador de jugadores online.
     * — Elimina sus nombres de la lista hover.
     * Compatible con ETCCore vanish y plugins con metadata "vanished" (DeluxeHub, etc.).
     */
    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        VanishManager vm = plugin.getVanishManager();

        long vanishedCount = Bukkit.getOnlinePlayers().stream()
                .filter(vm::isVanished)
                .count();

        if (vanishedCount == 0) return;

        event.setNumPlayers((int) Math.max(0, event.getNumPlayers() - vanishedCount));
        event.getPlayerSample().removeIf(profile -> {
            if (profile.getId() == null) return false;
            Player p = Bukkit.getPlayer(profile.getId());
            return p != null && vm.isVanished(p);
        });
    }
}

