package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.manager.TeleportManager.TeleportType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Lista paginada de warps públicos. */
public class WarpsMenu extends EtcMenu {

    private static final int PAGE_SIZE = 28;
    private static final int[] SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final int page;
    private List<String> warps;

    public WarpsMenu(ETCCore plugin, Player player, int page) {
        super(plugin, player);
        this.page = Math.max(0, page);
    }

    @Override public String title() { return "&8» &bWarps &7(pág. " + (page + 1) + ")"; }
    @Override public int rows() { return 6; }

    @Override
    public void build() {
        warps = plugin.getTeleportManager().listWarps();

        if (warps.isEmpty()) {
            set(22, item(Material.PAPER, "&7No hay warps disponibles"));
            footer(false, false);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        int from = page * PAGE_SIZE;
        int to   = Math.min(warps.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            String name = warps.get(i);
            Location loc = plugin.getTeleportManager().getWarp(name);
            String world = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "?";
            set(SLOTS[i - from], item(Material.COMPASS,
                    "&b" + name,
                    "&8• &7Mundo: &f" + world,
                    "",
                    "&eClick para teletransportarte"));
        }

        boolean prev = page > 0;
        boolean next = to < warps.size();
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
        if (slot == 45) { new MainMenu(plugin, player).open(); return; }
        if (slot == 48) { new WarpsMenu(plugin, player, page - 1).open(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 50) { new WarpsMenu(plugin, player, page + 1).open(); return; }

        if (warps == null) return;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            int index = page * PAGE_SIZE + i;
            if (index >= warps.size()) return;
            String name = warps.get(index);
            Location loc = plugin.getTeleportManager().getWarp(name);
            if (loc == null) {
                player.sendMessage("§cWarp §e" + name + "§c no encontrado.");
                return;
            }
            player.closeInventory();
            plugin.getTeleportManager().executeTeleport(player, loc,
                    "§aTeletransportado al warp §e" + name + "§a.",
                    TeleportType.WARP, false);
            return;
        }
    }
}
