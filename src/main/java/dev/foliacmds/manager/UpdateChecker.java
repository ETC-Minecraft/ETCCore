package dev.foliacmds.manager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;

/**
 * Checks GitHub Releases API for a newer version of ETCCore.
 * Only notifies online players with fccmds.admin permission, never regular players.
 * All HTTP calls are async — never blocks the main thread.
 */
public class UpdateChecker implements Listener {

    private static final String API_URL =
            "https://api.github.com/repos/ETC-Minecraft/ETCCore/releases/latest";
    private static final String PERM = "fccmds.admin";

    private final JavaPlugin plugin;
    private volatile String  latestVersion  = null;
    private volatile boolean updateAvailable = false;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Run an async version check. Call once from onEnable. */
    public void check() {
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(6))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "ETCCore/" + plugin.getDescription().getVersion())
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String body = response.body();
                    int idx = body.indexOf("\"tag_name\"");
                    if (idx >= 0) {
                        int start = body.indexOf('"', idx + 10) + 1;
                        int end   = body.indexOf('"', start);
                        String tag = body.substring(start, end);
                        latestVersion   = tag.startsWith("v") ? tag.substring(1) : tag;
                        String current  = plugin.getDescription().getVersion();
                        updateAvailable = !latestVersion.equals(current);
                        if (updateAvailable) {
                            plugin.getLogger().info(
                                    "[UpdateChecker] Nueva versión disponible: v" + latestVersion +
                                    " (actual: v" + current + ")");
                            plugin.getLogger().info(
                                    "[UpdateChecker] https://github.com/ETC-Minecraft/ETCCore/releases/latest");
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE,
                        "[UpdateChecker] No se pudo verificar: " + e.getMessage());
            }
        });
    }

    /** Notifies admins of the update when they join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(PERM)) return;
        // Slight delay so the message appears after join messages
        player.getScheduler().runDelayed(plugin, t ->
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "§6[ETCCore] §eNueva versión disponible: §av" + latestVersion +
                        " §7(actual: §fv" + plugin.getDescription().getVersion() + "§7)" +
                        " — §bhttps://github.com/ETC-Minecraft/ETCCore/releases/latest")),
                null, 40L);
    }
}
