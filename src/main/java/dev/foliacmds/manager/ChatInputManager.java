package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captura el siguiente mensaje de chat de un jugador y lo guarda como variable.
 * Se activa cuando un comando usa la acción [INPUT] variable;prompt.
 *
 * Thread-safe: usa ConcurrentHashMap (los eventos de chat son async en Paper).
 */
public class ChatInputManager implements Listener {

    private final FoliaCustomCommands plugin;

    // UUID → nombre de la variable que se está esperando
    private final ConcurrentHashMap<UUID, String> pending = new ConcurrentHashMap<>();

    public ChatInputManager(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    /**
     * Registra que el siguiente mensaje del jugador debe guardarse en varName.
     */
    public void expect(UUID uuid, String varName) {
        pending.put(uuid, varName);
    }

    /**
     * Cancela cualquier captura pendiente para ese jugador.
     */
    public void cancel(UUID uuid) {
        pending.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String varName = pending.remove(uuid);
        if (varName == null) return;

        // Cancelar el mensaje para que no aparezca en el chat del servidor
        event.setCancelled(true);

        String value = event.getMessage();
        plugin.getPlayerDataManager().set(uuid, varName, value);
        event.getPlayer().sendMessage("§a[✔] §7Variable §e" + varName + " §7guardada.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
