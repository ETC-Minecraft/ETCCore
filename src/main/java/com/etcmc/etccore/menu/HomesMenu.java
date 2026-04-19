package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.manager.TeleportManager.HomeRecord;
import com.etcmc.etccore.manager.TeleportManager.TeleportType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Lista paginada de homes del jugador.
 *
 * Controles:
 *   • Click izquierdo  → teletransporte
 *   • Click derecho    → /sethome con esta home en la ubicación actual (sobrescribe)
 *   • Q (drop)         → abre menú de confirmación de borrado
 */
public class HomesMenu extends EtcMenu {

    private static final int PAGE_SIZE = 28;
    private static final int[] SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final int page;
    private List<HomeRecord> homes;

    public HomesMenu(ETCCore plugin, Player player, int page) {
        super(plugin, player);
        this.page = Math.max(0, page);
    }

    @Override public String title() { return "&8» &6Homes &7(pág. " + (page + 1) + ")"; }
    @Override public int rows() { return 6; }

    @Override
    public void build() {
        homes = plugin.getTeleportManager().listHomes(player.getUniqueId());

        if (homes.isEmpty()) {
            set(22, item(Material.PAPER, "&7No tienes homes",
                    "&8Usa &f/sethome <nombre> &8para crear una."));
            footer(false, false);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        int from = page * PAGE_SIZE;
        int to   = Math.min(homes.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            HomeRecord h = homes.get(i);
            String world = h.location() != null && h.location().getWorld() != null
                    ? h.location().getWorld().getName() : "?";
            String coords = h.location() != null
                    ? String.format("&7%.0f, %.0f, %.0f", h.location().getX(), h.location().getY(), h.location().getZ())
                    : "&7?";
            set(SLOTS[i - from], item(h.isPublic() ? Material.LIME_BED : Material.RED_BED,
                    "&6" + h.name(),
                    "&8• &7Mundo: &f" + world,
                    "&8• " + coords,
                    "&8• " + (h.isPublic() ? "&aPública" : "&7Privada"),
                    "",
                    "&eIzq &7→ Teletransportarte",
                    "&eDer &7→ Mover aquí (sobrescribir)",
                    "&eQ   &7→ Borrar"));
        }

        boolean prev = page > 0;
        boolean next = to < homes.size();
        footer(prev, next);
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private void footer(boolean prev, boolean next) {
        set(45, backButton());
        if (prev) set(48, item(Material.ARROW, "&e« Página anterior"));
        set(49, closeButton());
        if (next) set(50, item(Material.ARROW, "&ePágina siguiente »"));
    }

    @Override
    public void onClick(int slot, ClickType click, ItemStack current) {
        if (slot == 45) { plugin.getMenuManager().openMenu(player, "menu"); return; }
        if (slot == 48) { new HomesMenu(plugin, player, page - 1).open(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 50) { new HomesMenu(plugin, player, page + 1).open(); return; }

        if (homes == null) return;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            int index = page * PAGE_SIZE + i;
            if (index >= homes.size()) return;
            HomeRecord h = homes.get(index);

            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                new HomeDeleteConfirmMenu(plugin, player, h.name(), this.page).open();
                return;
            }

            if (click.isRightClick()) {
                boolean ok = plugin.getTeleportManager().setHome(
                        player, h.name(), player.getLocation(), h.isPublic(), true);
                if (ok) {
                    player.sendMessage("§aHome §e" + h.name() + "§a movida a tu posición actual.");
                    refresh();
                } else {
                    player.sendMessage("§cNo se pudo actualizar la home §e" + h.name() + "§c.");
                }
                return;
            }

            // Click izquierdo (default)
            if (h.location() == null) {
                player.sendMessage("§cEsta home no tiene ubicación válida.");
                return;
            }
            player.closeInventory();
            plugin.getTeleportManager().executeTeleport(player, h.location(),
                    "§aTeletransportado a la home §e" + h.name() + "§a.",
                    TeleportType.HOME, false);
            return;
        }
    }
}
