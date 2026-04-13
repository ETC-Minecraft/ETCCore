package dev.foliacmds.command;

import dev.foliacmds.FoliaCustomCommands;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /invsee <jugador>   — Ve y edita el inventario completo de otro jugador.
 *                       Funciona tanto online como offline.
 *
 * Permisos:
 *   fccmds.invsee           — Acceso al comando
 *   fccmds.invsee.offline   — Ver/editar jugadores offline
 *
 * El inventario se muestra como un cofre de 54 slots:
 *   slots  0-35  → inventario principal (filas 4-1 del jugador, de abajo hacia arriba)
 *   slots 36-44  → hotbar (slots 0-8)
 *   slots 45-53  → armadura (casco, peto, pantalones, botas) + offhand
 *
 * Los cambios offline se guardan en playerdata al cerrar.
 */
public class InvSeeCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String PERM      = "fccmds.invsee";
    private static final String PERM_OFF  = "fccmds.invsee.offline";
    private static final int    INV_SIZE  = 54;

    private final FoliaCustomCommands plugin;
    private final ConcurrentHashMap<Inventory, OfflineInfo> openOfflineInvs = new ConcurrentHashMap<>();

    private record OfflineInfo(UUID uuid, String name) {}

    public InvSeeCommand(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission(PERM)) {
            player.sendMessage("§cNo tienes permiso para usar §e/invsee§c.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§eUso: §f/invsee <jugador>");
            return true;
        }

        String targetName = args[0];

        // --- Online ---
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            if (online.equals(player)) {
                player.sendMessage("§cNo puedes ver tu propio inventario con este comando.");
                return true;
            }
            player.getScheduler().run(plugin, t -> {
                Inventory view = buildOnlineView(online);
                player.openInventory(view);
                player.sendMessage("§7Viendo inventario de §e" + online.getName() + "§7.");
            }, null);
            return true;
        }

        // --- Offline ---
        if (!player.hasPermission(PERM_OFF)) {
            player.sendMessage("§cNo tienes permiso para ver inventarios de jugadores offline.");
            return true;
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            OfflinePlayer offline = lookupOfflinePlayer(targetName);
            if (offline == null || !offline.hasPlayedBefore()) {
                player.sendMessage("§cEl jugador §e" + targetName + " §cno existe o nunca ha jugado.");
                return;
            }

            Inventory inv = readOfflineInventory(offline);
            if (inv == null) {
                player.sendMessage("§cNo se pudo leer el inventario de §e" + offline.getName() + "§c.");
                return;
            }

            player.getScheduler().run(plugin, t2 -> {
                openOfflineInvs.put(inv, new OfflineInfo(offline.getUniqueId(), offline.getName()));
                player.openInventory(inv);
                player.sendMessage("§7Viendo inventario de §e" + offline.getName() + " §8(offline)§7.");
            }, null);
        });

        return true;
    }

    // -------------------------------------------------------------------------
    // Vista de jugador online: espejo en tiempo real abriendo su inventario
    // -------------------------------------------------------------------------
    private Inventory buildOnlineView(Player target) {
        Inventory view = Bukkit.createInventory(null, INV_SIZE,
                LegacyComponentSerializer.legacySection()
                        .deserialize("§9Inventario de §e" + target.getName()));

        ItemStack[] contents = target.getInventory().getContents(); // 36 slots
        ItemStack[] armor    = target.getInventory().getArmorContents(); // 4 slots
        ItemStack   offhand  = target.getInventory().getItemInOffHand();

        // Inventario principal (slots 9-35 del player → vista 0-26)
        // Hotbar (slots 0-8 del player → vista 27-35)
        // Para reflejar el orden visual del cliente:
        for (int i = 9; i < 36; i++) {
            view.setItem(i - 9, contents[i] != null ? contents[i].clone() : null);
        }
        for (int i = 0; i < 9; i++) {
            view.setItem(27 + i, contents[i] != null ? contents[i].clone() : null);
        }
        // Armadura: boots=0, leggings=1, chestplate=2, helmet=3
        for (int i = 0; i < 4; i++) {
            view.setItem(36 + i, armor[i] != null ? armor[i].clone() : null);
        }
        view.setItem(40, offhand != null ? offhand.clone() : null);

        return view;
    }

    // -------------------------------------------------------------------------
    // Cierre: guardar inventario offline si aplica
    // -------------------------------------------------------------------------
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        OfflineInfo info = openOfflineInvs.remove(inv);
        if (info == null) return;

        if (Bukkit.getPlayer(info.uuid()) != null) {
            if (event.getPlayer() instanceof Player p) {
                p.sendMessage("§e[Aviso] §7" + info.name() + " se conectó; los cambios §cno se guardaron§7.");
            }
            return;
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin,
                t -> saveOfflineInventory(info, inv,
                        event.getPlayer() instanceof Player p ? p : null));
    }

    // -------------------------------------------------------------------------
    // NMS: leer inventario offline del .dat
    // -------------------------------------------------------------------------
    private Inventory readOfflineInventory(OfflinePlayer offline) {
        File dataFile = getPlayerDataFile(offline.getUniqueId());
        if (!dataFile.exists()) return null;

        try {
            Class<?> accClass   = Class.forName("net.minecraft.nbt.NbtAccounter");
            Class<?> nbtIoClass = Class.forName("net.minecraft.nbt.NbtIo");
            Class<?> compClass  = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> listClass  = Class.forName("net.minecraft.nbt.ListTag");
            Class<?> tagClass   = Class.forName("net.minecraft.nbt.Tag");
            Class<?> nmsISClass = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> hlpClass   = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> mcClass    = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> craftClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");

            Object accounter = accClass.getMethod("unlimitedHeap").invoke(null);
            Object root = nbtIoClass.getMethod("readCompressed", Path.class, accClass)
                    .invoke(null, dataFile.toPath(), accounter);

            Object server     = mcClass.getMethod("getServer").invoke(null);
            Object registries = mcClass.getMethod("registryAccess").invoke(server);

            Method parseOptional = nmsISClass.getMethod("parseOptional", hlpClass, tagClass);
            Method isEmptyMethod = nmsISClass.getMethod("isEmpty");
            Method asBukkitCopy  = craftClass.getMethod("asBukkitCopy", nmsISClass);
            Method getCompound   = listClass.getMethod("getCompound", int.class);
            Method getByteMethod = compClass.getMethod("getByte", String.class);

            // "Inventory" en el .dat: slots 0-35 = items, 100-103 = armadura, -106 = offhand
            Object invList = compClass.getMethod("getList", String.class, int.class)
                    .invoke(root, "Inventory", 10);
            int size = (int) listClass.getMethod("size").invoke(invList);

            Inventory view = Bukkit.createInventory(null, INV_SIZE,
                    LegacyComponentSerializer.legacySection()
                            .deserialize("§9Inventario de §e" + offline.getName() + " §8(offline)"));

            for (int i = 0; i < size; i++) {
                Object itemTag = getCompound.invoke(invList, i);
                int rawSlot = ((byte) getByteMethod.invoke(itemTag, "Slot")) & 0xFF;

                Object nmsItem = parseOptional.invoke(null, registries, itemTag);
                if ((boolean) isEmptyMethod.invoke(nmsItem)) continue;
                ItemStack bukkit = (ItemStack) asBukkitCopy.invoke(null, nmsItem);

                // Mapear slots de Minecraft a la vista del cofre
                int viewSlot = mapSlotToView(rawSlot);
                if (viewSlot >= 0) view.setItem(viewSlot, bukkit);
            }

            return view;

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            plugin.getLogger().warning("Error leyendo inventario de " + offline.getName() + ": " + cause);
            return null;
        }
    }

    /**
     * Convierte el slot nativo del .dat a la posición en la vista de cofre.
     * Inventario principal: 9-35 → vista 0-26; Hotbar: 0-8 → vista 27-35
     * Armadura: 100(botas)→36, 101(pantalones)→37, 102(peto)→38, 103(casco)→39
     * Offhand: 150 (signed -106) → vista 40
     */
    private int mapSlotToView(int raw) {
        if (raw >= 9 && raw <= 35) return raw - 9;      // inventario fila 2-4
        if (raw >= 0 && raw <= 8)  return raw + 27;     // hotbar
        if (raw >= 100 && raw <= 103) return raw - 64;  // armadura (100→36 … 103→39)
        if (raw == 150 || raw == (256 - 106)) return 40; // offhand
        return -1;
    }

    // -------------------------------------------------------------------------
    // NMS: guardar inventario offline al .dat
    // -------------------------------------------------------------------------
    private void saveOfflineInventory(OfflineInfo info, Inventory view, Player notifyPlayer) {
        File dataFile = getPlayerDataFile(info.uuid());
        if (!dataFile.exists()) return;

        try {
            Class<?> accClass   = Class.forName("net.minecraft.nbt.NbtAccounter");
            Class<?> nbtIoClass = Class.forName("net.minecraft.nbt.NbtIo");
            Class<?> compClass  = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> listClass  = Class.forName("net.minecraft.nbt.ListTag");
            Class<?> tagClass   = Class.forName("net.minecraft.nbt.Tag");
            Class<?> nmsISClass = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> hlpClass   = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> mcClass    = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> craftClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");

            Object accounter = accClass.getMethod("unlimitedHeap").invoke(null);
            Object root = nbtIoClass.getMethod("readCompressed", Path.class, accClass)
                    .invoke(null, dataFile.toPath(), accounter);

            Object server     = mcClass.getMethod("getServer").invoke(null);
            Object registries = mcClass.getMethod("registryAccess").invoke(server);

            Method asNMSCopy  = craftClass.getMethod("asNMSCopy", ItemStack.class);
            Method saveItem   = nmsISClass.getMethod("save", hlpClass);
            Method isEmptyNms = nmsISClass.getMethod("isEmpty");
            Method putByte    = compClass.getMethod("putByte", String.class, byte.class);

            Object newList = listClass.getDeclaredConstructor().newInstance();
            Method listAdd = java.util.List.class.getMethod("add", Object.class);

            // Reconstruir los 41 slots que se muestran en la vista
            for (int viewSlot = 0; viewSlot < INV_SIZE; viewSlot++) {
                ItemStack bukkit = view.getItem(viewSlot);
                if (bukkit == null || bukkit.getType().isAir()) continue;

                Object nmsItem = asNMSCopy.invoke(null, bukkit);
                if ((boolean) isEmptyNms.invoke(nmsItem)) continue;

                Object itemTag = saveItem.invoke(nmsItem, registries);
                byte nativeSlot = (byte) viewToNativeSlot(viewSlot);
                putByte.invoke(itemTag, "Slot", nativeSlot);
                listAdd.invoke(newList, itemTag);
            }

            compClass.getMethod("put", String.class, tagClass).invoke(root, "Inventory", newList);
            nbtIoClass.getMethod("writeCompressed", compClass, Path.class).invoke(null, root, dataFile.toPath());

            if (notifyPlayer != null) {
                notifyPlayer.sendMessage("§a[InvSee] §7Inventario de §e" + info.name() + " §7guardado.");
            }

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            plugin.getLogger().warning("Error guardando inventario de " + info.name() + ": " + cause);
        }
    }

    private int viewToNativeSlot(int viewSlot) {
        if (viewSlot >= 0  && viewSlot <= 26) return viewSlot + 9;
        if (viewSlot >= 27 && viewSlot <= 35) return viewSlot - 27;
        if (viewSlot >= 36 && viewSlot <= 39) return viewSlot + 64;  // 36→100 … 39→103
        if (viewSlot == 40) return 150;                               // offhand
        return -1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    @SuppressWarnings("deprecation")
    private OfflinePlayer lookupOfflinePlayer(String name) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return cached;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(op.getName())) return op;
        }
        return null;
    }

    private File getPlayerDataFile(UUID uuid) {
        World world = Bukkit.getWorlds().get(0);
        return new File(world.getWorldFolder(), "playerdata/" + uuid + ".dat");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) return List.of();
        if (!player.hasPermission(PERM)) return List.of();
        String partial = args[0].toLowerCase();
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(partial)) out.add(p.getName());
        }
        return out;
    }
}
