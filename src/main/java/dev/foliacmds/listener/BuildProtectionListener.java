package dev.foliacmds.listener;

import dev.foliacmds.FoliaCustomCommands;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Protección de construcción basada en el nodo de permiso "fccmds.build".
 *
 * Uso en LuckPerms:
 *   • El nodo viene con default: op en plugin.yml → por defecto solo OPs construyen.
 *   • Para dar acceso al grupo aprobado:
 *       /lp group cometa permission set fccmds.build true
 *
 * Se puede desactivar en config.yml:
 *   build-protection: false
 */
public class BuildProtectionListener implements Listener {

    private static final String PERM        = "fccmds.build";
    private static final String PERM_BYPASS = "fccmds.build.bypass";
    private static final String PERM_ITEMS  = "fccmds.items";

    private final FoliaCustomCommands plugin;

    public BuildProtectionListener(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (canBuild(player)) return;
        event.setCancelled(true);
        sendDenied(player);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (canBuild(player)) return;
        event.setCancelled(true);
        sendDenied(player);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (canBuild(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("item-protection", true)) return;
        Player player = event.getPlayer();
        if (canItems(player)) return;
        event.setCancelled(true);
        String msg = plugin.getConfig().getString(
                "item-protection-message", "&cNo puedes tirar objetos aquí.");
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!plugin.getConfig().getBoolean("item-protection", true)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (canItems(player)) return;
        event.setCancelled(true);
    }

    private boolean canBuild(Player player) {
        if (player.hasPermission(PERM_BYPASS)) return true;
        if (player.getGameMode() == GameMode.SPECTATOR) return true;
        return player.hasPermission(PERM);
    }

    private boolean canItems(Player player) {
        if (player.hasPermission(PERM_BYPASS)) return true;
        if (player.getGameMode() == GameMode.SPECTATOR) return true;
        return player.hasPermission(PERM_ITEMS);
    }

    private void sendDenied(Player player) {
        String msg = plugin.getConfig().getString(
                "build-protection-message",
                "&cNecesitas ser aprobado para construir o romper bloques.");
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }
}
