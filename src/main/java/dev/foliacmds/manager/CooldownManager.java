package dev.foliacmds.manager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona cooldowns por jugador y por comando, y cooldowns globales.
 * Thread-safe: usa ConcurrentHashMap para compatibilidad con Folia.
 */
public class CooldownManager {

    // UUID del jugador → (nombre del comando → timestamp de expiración en ms)
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> cooldowns
            = new ConcurrentHashMap<>();

    // Cooldowns globales: nombre del comando → timestamp de expiración en ms
    private final ConcurrentHashMap<String, Long> globalCooldowns = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Cooldown por jugador
    // -------------------------------------------------------------------------
    public boolean isOnCooldown(UUID uuid, String command) {
        ConcurrentHashMap<String, Long> map = cooldowns.get(uuid);
        if (map == null) return false;
        Long expiry = map.get(command);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /** Devuelve los segundos restantes, redondeando hacia arriba. */
    public long getRemainingSeconds(UUID uuid, String command) {
        ConcurrentHashMap<String, Long> map = cooldowns.get(uuid);
        if (map == null) return 0;
        Long expiry = map.get(command);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0L, (remaining + 999L) / 1000L);
    }

    public void setCooldown(UUID uuid, String command, long seconds) {
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(command, System.currentTimeMillis() + seconds * 1000L);
    }

    // -------------------------------------------------------------------------
    // Cooldown global (afecta a TODOS los jugadores)
    // -------------------------------------------------------------------------
    public boolean isOnGlobalCooldown(String command) {
        Long expiry = globalCooldowns.get(command);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public long getGlobalRemainingSeconds(String command) {
        Long expiry = globalCooldowns.get(command);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0L, (remaining + 999L) / 1000L);
    }

    public void setGlobalCooldown(String command, long seconds) {
        globalCooldowns.put(command, System.currentTimeMillis() + seconds * 1000L);
    }

    // -------------------------------------------------------------------------
    // Limpieza periódica
    // -------------------------------------------------------------------------
    /** Elimina entradas expiradas para evitar acumulación de memoria. */
    public void clearExpired() {
        long now = System.currentTimeMillis();
        cooldowns.forEach((uuid, map) ->
                map.entrySet().removeIf(e -> e.getValue() <= now));
        cooldowns.entrySet().removeIf(e -> e.getValue().isEmpty());
        globalCooldowns.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
