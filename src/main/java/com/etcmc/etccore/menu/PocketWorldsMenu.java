package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.bridge.ETCWorldsBridge;
import com.etcmc.etccore.manager.TeleportManager.TeleportType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Lista paginada de PocketWorlds visibles para el jugador. */
public class PocketWorldsMenu extends EtcMenu {

    private static final int PAGE_SIZE = 28;
    private static final int[] SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final int page;
    private List<ETCWorldsBridge.PocketEntry> entries;

    public PocketWorldsMenu(ETCCore plugin, Player player, int page) {
        super(plugin, player);
        this.page = Math.max(0, page);
    }

    @Override public String title() { return "&8» &dPocketWorlds &7(pág. " + (page + 1) + ")"; }
    @Override public int rows() { return 6; }

    @Override
    public void build() {
        ETCWorldsBridge bridge = plugin.getETCWorldsBridge();
        if (bridge == null || !bridge.isAvailable()) {
            set(22, item(Material.BARRIER, "&cETCWorlds no disponible"));
            footer(false, false);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        boolean bypass = player.hasPermission("etccore.menu.bypass");
        entries = bridge.visiblePocketWorlds(player, bypass);

        if (entries.isEmpty()) {
            set(22, item(Material.PAPER, "&7No tienes acceso a ningún PocketWorld",
                    "&8Crea el tuyo con &f/pw create &8o pide invitación."));
            footer(false, false);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        int from = page * PAGE_SIZE;
        int to   = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ETCWorldsBridge.PocketEntry pw = entries.get(i);
            boolean own = pw.owner().equals(player.getUniqueId());
            set(SLOTS[i - from], head(pw.owner(),
                    (own ? "&aTu PocketWorld" : "&dPocketWorld de &f" + pw.ownerName()),
                    "&8• &7Mundo: &f" + pw.worldName(),
                    "",
                    "&eClick para teletransportarte"));
        }

        boolean prev = page > 0;
        boolean next = to < entries.size();
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
        if (slot == 48) { new PocketWorldsMenu(plugin, player, page - 1).open(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 50) { new PocketWorldsMenu(plugin, player, page + 1).open(); return; }

        if (entries == null) return;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            int index = page * PAGE_SIZE + i;
            if (index >= entries.size()) return;
            ETCWorldsBridge.PocketEntry pw = entries.get(index);
            Location spawn = plugin.getETCWorldsBridge().getSpawn(pw.worldName());
            if (spawn == null) {
                player.sendMessage("§cEl mundo §e" + pw.worldName() + "§c no está cargado.");
                return;
            }
            player.closeInventory();
            plugin.getTeleportManager().executeTeleport(player, spawn,
                    "§aTeletransportado al PocketWorld de §e" + pw.ownerName() + "§a.",
                    TeleportType.WARP, false);
            return;
        }
    }
}
