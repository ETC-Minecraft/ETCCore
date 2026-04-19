package com.etcmc.etccore.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Listener único para todos los menús que extienden {@link EtcMenu}.
 * Se identifica el menú por su InventoryHolder.
 */
public class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof EtcMenu menu)) return;
        e.setCancelled(true); // siempre bloquear movimiento de items
        // Click en el inventario propio del jugador → ignorar
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
        try {
            menu.onClick(e.getRawSlot(), e.getClick(), e.getCurrentItem());
        } catch (Exception ex) {
            e.getWhoClicked().sendMessage("§cError en menú: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof EtcMenu) e.setCancelled(true);
    }
}
