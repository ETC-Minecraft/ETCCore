package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.manager.TeleportManager.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Solicitudes TPA pendientes.
 *   • Click izquierdo  → /tpaccept <nombre>
 *   • Click derecho    → /tpdeny <nombre>
 */
public class TPAMenu extends EtcMenu {

    private static final int[] SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
    };

    private List<TeleportRequest> reqs;

    public TPAMenu(ETCCore plugin, Player player) { super(plugin, player); }

    @Override public String title() { return "&8» &5Solicitudes TPA"; }
    @Override public int rows() { return 5; }

    @Override
    public void build() {
        reqs = plugin.getTeleportManager().getRequests(player.getUniqueId());

        if (reqs.isEmpty()) {
            set(22, item(Material.PAPER, "&7No tienes solicitudes pendientes",
                    "&8Aparecerán aquí cuando alguien use &f/tpa &8o &f/tpahere&8."));
        } else {
            int max = Math.min(reqs.size(), SLOTS.length);
            for (int i = 0; i < max; i++) {
                TeleportRequest r = reqs.get(i);
                OfflinePlayer op = Bukkit.getOfflinePlayer(r.requesterId());
                String name = op.getName() != null ? op.getName() : r.requesterId().toString().substring(0, 8);
                long ageSec = (System.currentTimeMillis() - r.createdAt()) / 1000L;
                String type = r.type().name(); // TPA / TPAHERE / IGNORED
                set(SLOTS[i], head(r.requesterId(),
                        "&5" + name,
                        "&8• &7Tipo: &f" + type,
                        "&8• &7Hace &f" + ageSec + "s",
                        "",
                        "&aIzq &7→ Aceptar",
                        "&cDer &7→ Rechazar"));
            }
        }

        set(40, backButton());
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(int slot, ClickType click, ItemStack current) {
        if (slot == 40) { plugin.getMenuManager().openMenu(player, "menu"); return; }
        if (reqs == null) return;

        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            if (i >= reqs.size()) return;
            TeleportRequest r = reqs.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(r.requesterId());
            String name = op.getName() != null ? op.getName() : r.requesterId().toString();

            String cmd = click.isRightClick() ? "tpdeny " + name : "tpaccept " + name;
            player.closeInventory();
            Bukkit.dispatchCommand(player, cmd);
            return;
        }
    }
}
