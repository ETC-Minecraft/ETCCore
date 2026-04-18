package com.etcmc.etccore.manager;

import com.etcmc.etccore.ETCCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona el vanish nativo de ETCCore.
 * Controla visibilidad del jugador, TAB list y MOTD ping.
 *
 * También sincroniza automáticamente la metadata "vanished" de plugins externos
 * (DeluxeHub, etc.) para que el TAB list y el MOTD los oculten también.
 */
public class VanishManager {

    private final ETCCore plugin;

    /** UUIDs de jugadores actualmente en vanish por ETCCore. */
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    /**
     * Estado anterior de hidePlayer por metadata externa, para detectar cambios.
     * UUID = jugadores que en el último tick tenían metadata "vanished" de otro plugin.
     */
    private final Set<UUID> externalVanished = ConcurrentHashMap.newKeySet();

    public VanishManager(ETCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia la tarea de sincronización periódica.
     * Debe llamarse después de que el scheduler esté disponible (en onEnable).
     * Corre cada 20 ticks (1 segundo) para detectar cambios de metadata de plugins externos.
     */
    public void startSyncTask() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, t -> syncExternalVanish(), 20L, 20L);
    }

    /**
     * Sincroniza la metadata "vanished" de plugins externos (DeluxeHub, etc.) con hidePlayer.
     * Se ejecuta cada segundo. Detecta quién ganó o perdió la metadata y aplica hidePlayer/showPlayer.
     */
    private void syncExternalVanish() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            // Solo manejar jugadores en vanish externo (no el de ETCCore, que ya lo gestiona aparte)
            boolean hasExternalMeta = player.hasMetadata("vanished")
                    && !vanished.contains(uuid);

            if (hasExternalMeta && externalVanished.add(uuid)) {
                // Pasó a estado vanish externo → aplicar hidePlayer
                applyHide(player);
            } else if (!hasExternalMeta && externalVanished.remove(uuid)) {
                // Perdió la metadata externa → mostrar si tampoco está en vanish ETC
                if (!vanished.contains(uuid)) {
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        other.showPlayer(plugin, player);
                    }
                }
            }
        }
        // Limpiar UUIDs de jugadores desconectados
        externalVanished.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    /**
     * Devuelve true si el jugador está en vanish, ya sea por ETCCore
     * o por cualquier plugin que use la metadata estándar "vanished" (DeluxeHub, etc.).
     */
    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId()) || player.hasMetadata("vanished");
    }

    public boolean isVanishedByETC(UUID uuid) {
        return vanished.contains(uuid);
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    /** Alterna el estado de vanish del jugador. Devuelve true si ahora está en vanish. */
    public boolean toggle(Player target) {
        if (vanished.contains(target.getUniqueId())) {
            unvanish(target);
            return false;
        } else {
            vanish(target);
            return true;
        }
    }

    public void vanish(Player target) {
        vanished.add(target.getUniqueId());
        applyHide(target);
        target.sendMessage(Component.text()
                .append(Component.text("[Vanish] ", NamedTextColor.GRAY))
                .append(Component.text("Ahora estás en vanish.", NamedTextColor.GREEN))
                .build());
    }

    public void unvanish(Player target) {
        vanished.remove(target.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, target);
        }
        target.sendMessage(Component.text()
                .append(Component.text("[Vanish] ", NamedTextColor.GRAY))
                .append(Component.text("Ya no estás en vanish.", NamedTextColor.RED))
                .build());
    }

    /** Aplica hidePlayer para un jugador a todos los que no tengan permiso de ver vanish. */
    private void applyHide(Player target) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(target.getUniqueId())
                    && !other.hasPermission("etccore.vanish")) {
                other.hidePlayer(plugin, target);
            }
        }
    }

    // ── Eventos de join ───────────────────────────────────────────────────────

    /**
     * Llamar en PlayerJoinEvent:
     * Oculta todos los jugadores vanished al nuevo jugador (si no tiene permiso).
     */
    public void applyToJoiner(Player joiner) {
        boolean canSee = joiner.hasPermission("etccore.vanish");
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(joiner.getUniqueId())
                    && isVanished(online) && !canSee) {
                joiner.hidePlayer(plugin, online);
            }
        }
    }

    /**
     * Llamar cuando un jugador vanished (re)conecta:
     * Lo oculta de todos los jugadores sin permiso ya conectados.
     */
    public void applyOnReJoin(Player vanishedPlayer) {
        applyHide(vanishedPlayer);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    public Set<UUID> getVanishedSet() {
        return Collections.unmodifiableSet(vanished);
    }

    /** Elimina al jugador del set al desconectarse (el vanish no persiste entre sesiones). */
    public void onDisconnect(Player player) {
        vanished.remove(player.getUniqueId());
        externalVanished.remove(player.getUniqueId());
    }
}
