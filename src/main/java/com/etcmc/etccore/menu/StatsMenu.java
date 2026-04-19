package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.listener.PVPListener;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Información personal del jugador. */
public class StatsMenu extends EtcMenu {

    public StatsMenu(ETCCore plugin, Player player) { super(plugin, player); }

    @Override public String title() { return "&8» &eEstadísticas"; }
    @Override public int rows() { return 3; }

    @Override
    public void build() {
        // Cabeza
        set(4, head(player.getUniqueId(),
                "&e" + player.getName(),
                "&7" + (player.hasPermission("etccore.menu.bypass") ? "Staff" : "Jugador")));

        // Tiempo de juego
        String playtime = plugin.getPlaytimeManager() != null
                ? plugin.getPlaytimeManager().getPlaytimeHuman(player) : "?";
        set(10, item(Material.CLOCK, "&bTiempo de juego",
                "&8• &f" + playtime));

        // Homes
        int homes = plugin.getTeleportManager().listHomes(player.getUniqueId()).size();
        set(12, item(Material.RED_BED, "&6Homes",
                "&8• &fGuardadas: &e" + homes));

        // PvP
        boolean pvp = plugin.getPlayerDataManager()
                .getBool(player.getUniqueId(), PVPListener.VAR, PVPListener.DEFAULT);
        set(14, item(pvp ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD,
                "&cPvP",
                "&8• " + (pvp ? "&aActivado" : "&7Desactivado")));

        // Balance (Vault)
        if (plugin.getVaultManager() != null && plugin.getVaultManager().isEnabled()) {
            double bal = plugin.getVaultManager().getBalance(player);
            set(16, item(Material.GOLD_INGOT, "&6Balance",
                    "&8• &f" + plugin.getVaultManager().format(bal)));
        } else {
            set(16, item(Material.IRON_INGOT, "&7Balance",
                    "&8• &7Vault no disponible"));
        }

        set(22, backButton());
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(int slot, ClickType click, ItemStack current) {
        if (slot == 22) plugin.getMenuManager().openMenu(player, "menu");
    }
}
