package dev.foliacmds.listener;

import dev.foliacmds.FoliaCustomCommands;
import dev.foliacmds.manager.CommandBlockManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

/**
 * Intercepta ejecución de comandos y autocompletado para aplicar las reglas
 * definidas en blocked-commands.yml.
 *
 * Prioridad LOWEST para actuar antes que cualquier plugin de comandos.
 */
public class CommandBlockListener implements Listener {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final CommandBlockManager manager;

    public CommandBlockListener(FoliaCustomCommands plugin) {
        this.manager = plugin.getCommandBlockManager();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith("/")) return;

        String label = msg.substring(1).split("\\s+")[0];
        String labelNoNs = label.contains(":") ? label.split(":", 2)[1] : label;

        String blockMsg = manager.getBlockMessage(event.getPlayer(), labelNoNs);
        if (blockMsg == null) blockMsg = manager.getBlockMessage(event.getPlayer(), label);
        if (blockMsg == null) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(LEGACY.deserialize(blockMsg));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) return;
        String buffer = event.getBuffer();
        if (!buffer.startsWith("/")) return;
        if (buffer.contains(" ")) return;

        event.getCompletions().removeIf(completion -> {
            String plain = completion.contains(":") ? completion.split(":", 2)[1] : completion;
            return manager.isHidden(player, plain) || manager.isHidden(player, completion);
        });
    }
}
