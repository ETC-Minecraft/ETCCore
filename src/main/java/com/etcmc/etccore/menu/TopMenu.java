package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Top de jugadores por tiempo de juego (sólo jugadores online actualmente).
 * Para ranking offline completo se necesitaría leer estadísticas en disco.
 */
public class TopMenu extends EtcMenu {

    private static final int PAGE_SIZE = 28;
    private static final int[] SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final int page;
    private List<Player> ranked;

    public TopMenu(ETCCore plugin, Player player, int page) {
        super(plugin, player);
        this.page = Math.max(0, page);
    }

    @Override public String title() { return "&8» &6Top jugadores &7(pág. " + (page + 1) + ")"; }
    @Override public int rows() { return 6; }

    @Override
    public void build() {
        ranked = new ArrayList<>(Bukkit.getOnlinePlayers());
        ranked.sort(Comparator.comparingLong(plugin.getPlaytimeManager()::getPlaytimeSeconds).reversed());

        if (ranked.isEmpty()) {
            set(22, item(Material.PAPER, "&7No hay jugadores online"));
        } else {
            int from = page * PAGE_SIZE;
            int to   = Math.min(ranked.size(), from + PAGE_SIZE);
            for (int i = from; i < to; i++) {
                Player p = ranked.get(i);
                String time = plugin.getPlaytimeManager().getPlaytimeHuman(p);
                int rank = i + 1;
                set(SLOTS[i - from], head(p.getUniqueId(),
                        "&6#" + rank + " &f" + p.getName(),
                        "&8• &7Tiempo: &e" + time));
            }
        }

        boolean prev = page > 0;
        boolean next = ranked.size() > (page + 1) * PAGE_SIZE;
        set(45, backButton());
        if (prev) set(48, item(Material.ARROW, "&e« Página anterior"));
        set(49, closeButton());
        if (next) set(50, item(Material.ARROW, "&ePágina siguiente »"));
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(int slot, ClickType click, ItemStack current) {
        if (slot == 45) new MainMenu(plugin, player).open();
        else if (slot == 48) new TopMenu(plugin, player, page - 1).open();
        else if (slot == 49) player.closeInventory();
        else if (slot == 50) new TopMenu(plugin, player, page + 1).open();
    }
}
