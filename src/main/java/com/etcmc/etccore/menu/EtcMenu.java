package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Base de todos los menús personalizados de ETCCore.
 * Implementa InventoryHolder para que el listener pueda detectarnos sin
 * mantener un mapa global de jugadores → menú abierto.
 */
public abstract class EtcMenu implements InventoryHolder {

    protected final ETCCore plugin;
    protected final Player  player;
    protected       Inventory inventory;

    protected EtcMenu(ETCCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public abstract String title();
    public abstract int rows();      // 1..6
    public abstract void build();
    public abstract void onClick(int slot, ClickType click, ItemStack current);

    public void open() {
        inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows())) * 9, color(title()));
        build();
        player.openInventory(inventory);
    }

    /** Reabre con build() actualizado (útil para menús dinámicos como toggles). */
    public void refresh() {
        if (inventory == null) { open(); return; }
        inventory.clear();
        build();
    }

    @Override public Inventory getInventory() { return inventory; }

    // ── Helpers ──────────────────────────────────────────────────────────

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    protected void set(int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, stack);
    }

    protected ItemStack item(Material m, String name, String... lore) {
        return item(m, 1, name, Arrays.asList(lore));
    }

    protected ItemStack item(Material m, int amount, String name, List<String> lore) {
        ItemStack s = new ItemStack(m, amount);
        ItemMeta meta = s.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> col = new ArrayList<>(lore.size());
                for (String l : lore) col.add(color(l));
                meta.setLore(col);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            s.setItemMeta(meta);
        }
        return s;
    }

    protected ItemStack head(UUID owner, String name, String... lore) {
        ItemStack s = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) s.getItemMeta();
        if (meta != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
            meta.setOwningPlayer(op);
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null && lore.length > 0) {
                List<String> col = new ArrayList<>(lore.length);
                for (String l : lore) col.add(color(l));
                meta.setLore(col);
            }
            s.setItemMeta(meta);
        }
        return s;
    }

    /** Llena los huecos vacíos del inventario con cristales decorativos. */
    protected void fillEmpty(Material decor) {
        ItemStack pane = item(decor, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, pane);
        }
    }

    /** Botón de "volver" estándar. */
    protected ItemStack backButton() {
        return item(Material.ARROW, "&c« Volver", "&7Click para regresar al menú principal.");
    }

    /** Botón de "cerrar" estándar. */
    protected ItemStack closeButton() {
        return item(Material.BARRIER, "&cCerrar");
    }
}
