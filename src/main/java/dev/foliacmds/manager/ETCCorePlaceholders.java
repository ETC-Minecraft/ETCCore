package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Expansión de PlaceholderAPI para ETCCore.
 *
 * Placeholders disponibles:
 *   %etccore_online%       — Jugadores online sin contar los que están en vanish.
 *   %etccore_staffonline%  — Staff online (permiso etccore.staff) sin contar vanish.
 */
public class ETCCorePlaceholders extends PlaceholderExpansion {

    private final FoliaCustomCommands plugin;

    public ETCCorePlaceholders(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "etccore";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
                ? "EmmanuelTC"
                : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** No queremos que PAPI nos desregistre al recargar. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "online" -> String.valueOf(countVisible());
            case "staffonline" -> String.valueOf(countVisibleStaff());
            default -> null;
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isVanished(Player p) {
        return plugin.getVanishManager().isVanished(p);
    }

    private long countVisible() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !isVanished(p))
                .count();
    }

    private long countVisibleStaff() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !isVanished(p))
                .filter(p -> p.hasPermission("etccore.staff"))
                .count();
    }
}
