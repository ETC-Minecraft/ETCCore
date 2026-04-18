package dev.foliacmds.listener;

import dev.foliacmds.FoliaCustomCommands;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class TeleportListener implements Listener {

    private final FoliaCustomCommands plugin;

    public TeleportListener(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        plugin.getTeleportManager().recordBackLocation(event.getPlayer().getUniqueId(), event.getFrom());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        plugin.getTeleportManager().recordDeath(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTeleportManager().recordLogout(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getTeleportManager().recordLogout(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        var pending = plugin.getTeleportManager().getPendingTeleport(event.getPlayer().getUniqueId());
        if (pending == null || !plugin.getTeleportManager().shouldCancelOnMove(pending.type())) {
            return;
        }
        if (!pending.origin().getWorld().equals(event.getTo().getWorld())) {
            plugin.getTeleportManager().cancelPendingTeleport(event.getPlayer(), "§cTeleport cancelado por movimiento.");
            return;
        }
        double maxDistance = plugin.getTeleportManager().getCancelMoveDistance();
        if (pending.origin().distanceSquared(event.getTo()) > (maxDistance * maxDistance)) {
            plugin.getTeleportManager().cancelPendingTeleport(event.getPlayer(), "§cTeleport cancelado por movimiento.");
        }
    }
}