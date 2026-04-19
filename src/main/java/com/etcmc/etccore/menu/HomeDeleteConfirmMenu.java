package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Confirmación para borrar una home. Lana verde = confirmar, lana roja = cancelar. */
public class HomeDeleteConfirmMenu extends EtcMenu {

    private final String homeName;
    private final int    returnPage;

    public HomeDeleteConfirmMenu(ETCCore plugin, Player player, String homeName, int returnPage) {
        super(plugin, player);
        this.homeName   = homeName;
        this.returnPage = returnPage;
    }

    @Override public String title() { return "&8» &c¿Borrar home '" + homeName + "'?"; }
    @Override public int rows() { return 3; }

    @Override
    public void build() {
        set(11, item(Material.LIME_WOOL,
                "&a✔ Confirmar borrado",
                "&7Eliminará la home &e" + homeName + "&7."));
        set(13, item(Material.RED_BED,
                "&6" + homeName,
                "&7Esta es la home que vas a borrar."));
        set(15, item(Material.RED_WOOL,
                "&c✘ Cancelar",
                "&7Vuelve sin borrar nada."));
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(int slot, ClickType click, ItemStack current) {
        if (slot == 11) {
            boolean ok = plugin.getTeleportManager().deleteHome(player.getUniqueId(), homeName);
            player.sendMessage(ok
                    ? "§aHome §e" + homeName + "§a borrada."
                    : "§cNo se pudo borrar la home §e" + homeName + "§c.");
            new HomesMenu(plugin, player, returnPage).open();
        } else if (slot == 15) {
            new HomesMenu(plugin, player, returnPage).open();
        }
    }
}
