package dev.foliacmds.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MuteManager {

    private final JavaPlugin plugin;
    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();
    private final File mutesFile;

    /**
     * @param endTime  Timestamp Unix en ms cuando expira el mute.
     *                 Usa -1 para indicar que es permanente.
     * @param reason   Razón visible para el jugador silenciado.
     */
    public record MuteEntry(long endTime, String reason) {

        public boolean isPermanent() { return endTime == -1L; }

        public boolean isExpired() {
            return !isPermanent() && System.currentTimeMillis() > endTime;
        }

        public String getRemainingFormatted() {
            if (isPermanent()) return "Permanente";
            return formatMillis(endTime - System.currentTimeMillis());
        }
    }

    public MuteManager(JavaPlugin plugin) {
        this.plugin = plugin;
        mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        load();
    }

    public void mute(UUID uuid, long endTime, String reason) {
        mutes.put(uuid, new MuteEntry(endTime, reason));
        save();
    }

    public void unmute(UUID uuid) {
        mutes.remove(uuid);
        save();
    }

    /** Comprueba si un jugador está silenciado. Limpia el mute automáticamente si expiró. */
    public boolean isMuted(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry == null) return false;
        if (entry.isExpired()) {
            mutes.remove(uuid);
            save();
            return false;
        }
        return true;
    }

    /** Devuelve la entrada de mute o null si no existe. No comprueba expiración. */
    public MuteEntry getEntry(UUID uuid) {
        return mutes.get(uuid);
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    private void load() {
        if (!mutesFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(mutesFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long endTime = cfg.getLong(key + ".end-time");
                String reason = cfg.getString(key + ".reason", "Sin razón");
                MuteEntry entry = new MuteEntry(endTime, reason);
                if (!entry.isExpired()) mutes.put(uuid, entry);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        mutes.forEach((uuid, entry) -> {
            if (!entry.isExpired()) {
                String k = uuid.toString();
                cfg.set(k + ".end-time", entry.endTime());
                cfg.set(k + ".reason",   entry.reason());
            }
        });
        try {
            cfg.save(mutesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar mutes.yml: " + e.getMessage());
        }
    }

    // ── Utilidad pública ──────────────────────────────────────────────────────

    /** Formatea una duración en milisegundos como texto legible (p.ej. "2h 30m 15s"). */
    public static String formatMillis(long ms) {
        if (ms <= 0) return "0s";
        long seconds = ms / 1000;
        long days    = seconds / 86400; seconds %= 86400;
        long hours   = seconds / 3600;  seconds %= 3600;
        long minutes = seconds / 60;    seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days    > 0) sb.append(days).append("d ");
        if (hours   > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
