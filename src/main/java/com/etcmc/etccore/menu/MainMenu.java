package com.etcmc.etccore.menu;

import com.etcmc.etccore.ETCCore;
import com.etcmc.etccore.bridge.ETCWorldsBridge;
import com.etcmc.etccore.listener.PVPListener;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Menú principal — punto de entrada de /menu.
 *
 * Layout (4 filas):
 *   Row 1: 10=Mundos  12=PocketWorlds  14=PvP  16=Homes
 *   Row 2: 19=Warps   21=TPA           23=Stats 25=Top
 *   Row 3: 31=Misiones
 *   Row 4: 35=Cerrar
 */
public class MainMenu extends EtcMenu {

    public MainMenu(ETCCore plugin, Player player) { super(plugin, player); }

    @Override public String title() { return "&8» &bMenú Principal"; }
    @Override public int rows() { return 4; }

    @Override
    public void build() {
        // Mundos
        set(10, item(Material.GRASS_BLOCK,
                "&aMundos",
                "&7Explora los mundos disponibles del servidor.",
                "",
                "&eClick para abrir"));

        // PocketWorlds
        ETCWorldsBridge bridge = plugin.getETCWorldsBridge();
        boolean hasOwn = bridge != null && bridge.hasOwnPocketWorld(player.getUniqueId());
        set(12, item(Material.ENDER_CHEST,
                "&dPocketWorlds",
                "&7Tu mundo personal y a los que has sido invitado.",
                hasOwn ? "&8• &aTienes un PocketWorld propio" : "&8• &7Aún no tienes uno",
                "",
                "&eClick para abrir"));

        // PvP toggle
        boolean pvpOn = plugin.getPlayerDataManager()
                .getBool(player.getUniqueId(), PVPListener.VAR, PVPListener.DEFAULT);
        set(14, item(pvpOn ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD,
                (pvpOn ? "&cPvP: &aActivado" : "&cPvP: &7Desactivado"),
                "&7Cuando está desactivado no recibes",
                "&7daño de otros jugadores y no puedes",
                "&7dañarlos a ellos.",
                "",
                "&eClick para alternar"));

        // Homes
        int homeCount = plugin.getTeleportManager().listHomes(player.getUniqueId()).size();
        set(16, item(Material.RED_BED,
                "&6Homes",
                "&7Tus puntos de teletransporte personales.",
                "&8• &fTienes &e" + homeCount + "&f homes guardadas",
                "",
                "&eClick para abrir"));

        // Warps
        int warpCount = plugin.getTeleportManager().listWarps().size();
        set(19, item(Material.COMPASS,
                "&bWarps",
                "&7Puntos públicos del servidor.",
                "&8• &f" + warpCount + " warps disponibles",
                "",
                "&eClick para abrir"));

        // TPA
        int requests = plugin.getTeleportManager().getRequests(player.getUniqueId()).size();
        set(21, item(Material.ENDER_PEARL,
                "&5Solicitudes TPA",
                "&7Acepta o rechaza solicitudes pendientes.",
                "&8• &fPendientes: &e" + requests,
                "",
                "&eClick para abrir"));

        // Stats
        set(23, item(Material.BOOK,
                "&eEstadísticas",
                "&7Tu información personal.",
                "",
                "&eClick para abrir"));

        // Top
        set(25, item(Material.GOLDEN_HELMET,
                "&6Top jugadores",
                "&7Ranking por tiempo de juego.",
                "",
                "&eClick para abrir"));

        // Misiones
        set(31, item(Material.WRITABLE_BOOK,
                "&aMisiones",
                "&8• &7Próximamente...",
                "",
                "&7Estamos trabajando en ello."));

        // Cerrar
        set(35, closeButton());

        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(int slot, ClickType click, ItemStack current) {
        switch (slot) {
            case 10 -> new WorldsMenu(plugin, player, 0).open();
            case 12 -> new PocketWorldsMenu(plugin, player, 0).open();
            case 14 -> {
                boolean now = plugin.getPlayerDataManager()
                        .toggleBool(player.getUniqueId(), PVPListener.VAR, PVPListener.DEFAULT);
                player.sendMessage(now ? "§cPvP §aactivado§7." : "§cPvP §7desactivado§7.");
                refresh();
            }
            case 16 -> new HomesMenu(plugin, player, 0).open();
            case 19 -> new WarpsMenu(plugin, player, 0).open();
            case 21 -> new TPAMenu(plugin, player).open();
            case 23 -> new StatsMenu(plugin, player).open();
            case 25 -> new TopMenu(plugin, player, 0).open();
            case 31 -> player.sendMessage("§a§lMisiones §7→ §fPróximamente");
            case 35 -> player.closeInventory();
            default -> {}
        }
    }
}
