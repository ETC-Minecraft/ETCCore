package dev.foliacmds.listener;

import dev.foliacmds.FoliaCustomCommands;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlaytimeListener implements Listener {

    private final FoliaCustomCommands plugin;

    public PlaytimeListener(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlaytimeManager().syncPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlaytimeManager().syncPlayer(event.getPlayer());
    }
}