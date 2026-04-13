package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;

/**
 * Protección de construcción basada en el nodo de permiso "fccmds.build".
 *
 * Uso en LuckPerms:
 *   • El nodo viene con default: op en plugin.yml  →  por defecto solo OPs construyen.
 *   • Para dar acceso al grupo aprobado (cometa/practicante/etc.):
 *       /lp group cometa permission set fccmds.build true
 *   • Para negar explícitamente al default aunque herede de otro grupo:
 *       /lp group nebulosa permission set fccmds.build false
 *
 * Se puede desactivar en config.yml:
 *   build-protection: false
 */
public class BuildProtectionListener implements Listener {

    private static final String PERM       = "fccmds.build";
    private static final String PERM_BYPASS = "fccmds.build.bypass"; // ignora la protección siempre

    private final FoliaCustomCommands plugin;

    public BuildProtectionListener(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    // ── Romper bloques ────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (canBuild(player)) return;
        event.setCancelled(true);
        sendDenied(player);
    }

    // ── Colocar bloques ───────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (canBuild(player)) return;
        event.setCancelled(true);
        sendDenied(player);
    }

    // ── Daño de bloque (animación de minado) ──────────────────────────────────
    // Cancelar esto evita que el bloque "se cuartee" visualmente aunque no se rompa,
    // lo que da una experiencia más limpia al jugador denegado.
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (canBuild(player)) return;
        event.setCancelled(true);
    }

    // ── Lógica de comprobación ────────────────────────────────────────────────
    private boolean canBuild(Player player) {
        // Bypass explícito (sirve para admins en cualquier gamemode)
        if (player.hasPermission(PERM_BYPASS)) return true;
        // Modo espectador nunca interactúa físicamente, no hace falta bloquear
        if (player.getGameMode() == GameMode.SPECTATOR)  return true;
        // Modo creativo: solo se bloquea si NO tiene el permiso base.
        // Así un admin en /gmc sigue teniendo acceso; un nebulosa en /gmc no.
        return player.hasPermission(PERM);
    }

    private void sendDenied(Player player) {
        String msg = plugin.getConfig().getString(
                "build-protection-message",
                "&cNo tienes permiso para construir o romper bloques.");
        player.sendActionBar(
                LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }
}
