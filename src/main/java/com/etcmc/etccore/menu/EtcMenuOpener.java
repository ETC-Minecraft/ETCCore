package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import org.bukkit.entity.Player;

/**
 * Entry point para abrir un submen\u00fa din\u00e1mico interno desde una accion YAML
 * {@code [ETCMENU] tipo} o desde codigo.
 *
 * Tipos soportados:
 * <ul>
 *   <li>mundos / worlds        \u2192 {@link WorldsMenu}</li>
 *   <li>pocketworlds / pw      \u2192 {@link PocketWorldsMenu}</li>
 *   <li>homes / home           \u2192 {@link HomesMenu}</li>
 *   <li>warps / warp           \u2192 {@link WarpsMenu}</li>
 *   <li>tpa / requests         \u2192 {@link TPAMenu}</li>
 *   <li>stats / estadisticas   \u2192 {@link StatsMenu}</li>
 *   <li>top                    \u2192 {@link TopMenu}</li>
 *   <li>pvp                    \u2192 toggle del flag PvP del jugador (sin GUI)</li>
 * </ul>
 *
 * Permiso requerido: {@code etccore.menu} (default true).
 */
public final class EtcMenuOpener {

    private EtcMenuOpener() {}

    public static void open(ETCCore plugin, Player p, String type) {
        if (p == null || type == null) return;
        if (!p.hasPermission("etccore.menu")) {
            p.sendMessage("\u00a7cNo tienes permiso para abrir ese men\u00fa.");
            return;
        }
        switch (type.toLowerCase()) {
            case "mundos", "worlds"        -> new WorldsMenu(plugin, p, 0).open();
            case "pocketworlds", "pw"      -> new PocketWorldsMenu(plugin, p, 0).open();
            case "homes", "home"           -> new HomesMenu(plugin, p, 0).open();
            case "warps", "warp"           -> new WarpsMenu(plugin, p, 0).open();
            case "tpa", "requests"         -> new TPAMenu(plugin, p).open();
            case "stats", "estadisticas"   -> new StatsMenu(plugin, p).open();
            case "top"                     -> new TopMenu(plugin, p, 0).open();
            case "pvp" -> {
                boolean newVal = plugin.getPlayerDataManager().toggleBool(p.getUniqueId(), "pvp", true);
                p.sendMessage(newVal
                        ? "\u00a7aPvP \u00a7ahabilitado\u00a77."
                        : "\u00a7aPvP \u00a7cdeshabilitado\u00a77.");
            }
            default -> p.sendMessage("\u00a7cSubmen\u00fa desconocido: \u00a77" + type
                    + "\u00a7c. V\u00e1lidos: mundos, pocketworlds, homes, warps, tpa, stats, top, pvp.");
        }
    }
}
