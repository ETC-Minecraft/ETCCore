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

/** Lista paginada de mundos creados (excluye PocketWorlds). */
public class WorldsMenu extends EtcMenu {

    private static final int PAGE_SIZE = 28; // filas 1..3, slots 10..16,19..25,28..34
    private static final int[] SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final int page;
    private List<ETCWorldsBridge.WorldEntry> entries;

    public WorldsMenu(ETCCore plugin, Player player, int page) {
        super(plugin, player);
        this.page = Math.max(0, page);
    }

    @Override public String title() { return "&8» &aMundos &7(pág. " + (page + 1) + ")"; }
    @Override public int rows() { return 6; }

    @Override
    public void build() {
        ETCWorldsBridge bridge = plugin.getETCWorldsBridge();
        if (bridge == null || !bridge.isAvailable()) {
            set(22, item(Material.BARRIER, "&cETCWorlds no disponible",
                    "&7El plugin ETCWorlds no está cargado",
                    "&7en este servidor."));
            footer(false, false);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        boolean bypass = player.hasPermission("etccore.menu.bypass");
        entries = bridge.visibleWorldsFor(player, bypass);

        if (entries.isEmpty()) {
            set(22, item(Material.PAPER, "&7No hay mundos visibles",
                    "&8Pide al staff que cree o publique algunos."));
            footer(false, false);
            fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        int from = page * PAGE_SIZE;
        int to   = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ETCWorldsBridge.WorldEntry w = entries.get(i);
            Material icon = w.template() ? Material.BOOKSHELF
                    : w.loaded() ? Material.GRASS_BLOCK : Material.DIRT;
            set(SLOTS[i - from], item(icon,
                    "&a" + w.name(),
                    w.template() ? "&8• &dPlantilla" : "&8• &7Mundo normal",
                    w.loaded()   ? "&8• &aCargado"  : "&8• &7No cargado",
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
        if (slot == 45) { plugin.getMenuManager().openMenu(player, "menu"); return; }
        if (slot == 48) { new WorldsMenu(plugin, player, page - 1).open(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 50) { new WorldsMenu(plugin, player, page + 1).open(); return; }

        if (entries == null) return;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            int index = page * PAGE_SIZE + i;
            if (index >= entries.size()) return;
            ETCWorldsBridge.WorldEntry w = entries.get(index);
            Location spawn = plugin.getETCWorldsBridge().getSpawn(w.name());
            if (spawn == null) {
                player.sendMessage("§cNo se pudo cargar el mundo §e" + w.name() + "§c.");
                return;
            }
            player.closeInventory();
            plugin.getTeleportManager().executeTeleport(player, spawn,
                    "§aTeletransportado a §e" + w.name() + "§a.",
                    TeleportType.WARP, false);
            return;
        }
    }
}
