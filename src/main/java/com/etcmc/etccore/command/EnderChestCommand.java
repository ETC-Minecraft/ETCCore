package com.etcmc.etccore.command;

import com.etcmc.etccore.ETCCore;
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
 * /enderchest              — Abre tu propio enderchest  (etccore.enderchest)
 * /enderchest <jugador>    — Abre el enderchest de otro (etccore.enderchest.others)
 *                            Funciona tanto con jugadores online como offline.
 *                            Los cambios hechos a jugadores offline se guardan al cerrar.
 *
 * Lee/escribe el playerdata/<uuid>.dat usando reflexión sobre las clases NMS
 * de Paper (net.minecraft.nbt.*, CraftItemStack). Preserva todos los
 * componentes del ítem (enchantments, nombre, lore, etc.) en 1.21+.
 */
public class EnderChestCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String PERM_SELF   = "etccore.enderchest";
    private static final String PERM_OTHERS = "etccore.enderchest.others";

    private final ETCCore plugin;

    /** Rastrea qué inventarios abiertos corresponden a qué jugador offline. */
    private final ConcurrentHashMap<Inventory, OfflineInfo> openOfflineChests = new ConcurrentHashMap<>();

    private record OfflineInfo(UUID uuid, String name) {}

    public EnderChestCommand(ETCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        if (args.length == 0) {
            // --- Propio enderchest ---
            if (!player.hasPermission(PERM_SELF)) {
                player.sendMessage("§cNo tienes permiso para usar §e/enderchest§c.");
                return true;
            }
            player.getScheduler().run(plugin,
                    t -> player.openInventory(player.getEnderChest()),
                    null);
            return true;
        }

        // --- Enderchest de otro jugador ---
        if (!player.hasPermission(PERM_OTHERS)) {
            player.sendMessage("§cNo tienes permiso para ver el enderchest de otros jugadores.");
            return true;
        }

        String targetName = args[0];

        // 1. Buscar online primero (más eficiente)
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            if (onlineTarget.equals(player)) {
                player.getScheduler().run(plugin,
                        t -> player.openInventory(player.getEnderChest()),
                        null);
            } else {
                player.getScheduler().run(plugin,
                        t -> {
                            player.openInventory(onlineTarget.getEnderChest());
                            player.sendMessage("§7Viendo el enderchest de §e" + onlineTarget.getName() + "§7.");
                        },
                        null);
            }
            return true;
        }

        // 2. Jugador offline: buscar en caché y leer su .dat de forma asíncrona
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            OfflinePlayer offline = lookupOfflinePlayer(targetName);
            if (offline == null || !offline.hasPlayedBefore()) {
                player.sendMessage("§cEl jugador §e" + targetName + " §cno existe o nunca ha jugado en este servidor.");
                return;
            }

            Inventory inv = readOfflineEnderChest(offline);
            if (inv == null) {
                player.sendMessage("§cNo se pudo leer el enderchest de §e" + offline.getName() + "§c.");
                return;
            }

            // Abrir el inventario en el hilo correcto de la región del jugador
            player.getScheduler().run(plugin,
                    t2 -> {
                        openOfflineChests.put(inv, new OfflineInfo(offline.getUniqueId(), offline.getName()));
                        player.openInventory(inv);
                        player.sendMessage("§7Viendo el enderchest de §e" + offline.getName() + " §8(offline)§7.");
                    },
                    null);
        });

        return true;
    }

    // -------------------------------------------------------------------------
    // Busca un OfflinePlayer por nombre (case-insensitive) desde la caché local.
    // Usa getOfflinePlayerIfCached para no hacer peticiones web a Mojang.
    // Si no está en caché, busca dentro de Bukkit.getOfflinePlayers().
    // -------------------------------------------------------------------------
    @SuppressWarnings("deprecation")
    private OfflinePlayer lookupOfflinePlayer(String name) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return cached;

        // Fallback asíncrono: iterar jugadores almacenados localmente
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(op.getName())) return op;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Lee el archivo playerdata/<uuid>.dat y construye un Inventory con los
    // ítems del enderchest (tag "EnderItems"). Usa reflexión sobre las clases
    // NMS (net.minecraft.nbt.*, CraftItemStack) que están presentes en runtime
    // pero no en la API pública de Paper.
    // Preserva enchantments, nombres y demás componentes del ítem en 1.21+.
    // -------------------------------------------------------------------------
    private Inventory readOfflineEnderChest(OfflinePlayer offline) {
        File dataFile = getPlayerDataFile(offline.getUniqueId());
        if (!dataFile.exists()) return null;

        try {
            // --- Clases NMS vía reflexión ---
            Class<?> accClass   = Class.forName("net.minecraft.nbt.NbtAccounter");
            Class<?> nbtIoClass = Class.forName("net.minecraft.nbt.NbtIo");
            Class<?> compClass  = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> listClass  = Class.forName("net.minecraft.nbt.ListTag");
            Class<?> tagClass   = Class.forName("net.minecraft.nbt.Tag");
            Class<?> nmsISClass = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> hlpClass   = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> mcClass    = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> craftClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");

            // NbtAccounter.unlimitedHeap()
            Object accounter = accClass.getMethod("unlimitedHeap").invoke(null);

            // NbtIo.readCompressed(Path, NbtAccounter) → CompoundTag  (Paper 1.20.5+)
            Object root = nbtIoClass.getMethod("readCompressed", Path.class, accClass)
                                    .invoke(null, dataFile.toPath(), accounter);

            // root.getList("EnderItems", 10 = TAG_Compound)  → ListTag
            Object enderList = compClass.getMethod("getList", String.class, int.class)
                                        .invoke(root, "EnderItems", 10);

            // ListTag.size()
            int size = (int) listClass.getMethod("size").invoke(enderList);

            // MinecraftServer.getServer().registryAccess()  → HolderLookup.Provider
            Object server     = mcClass.getMethod("getServer").invoke(null);
            Object registries = mcClass.getMethod("registryAccess").invoke(server);

            // Métodos que se reusan en el bucle
            Method parseOptional = nmsISClass.getMethod("parseOptional", hlpClass, tagClass);
            Method isEmptyMethod = nmsISClass.getMethod("isEmpty");
            Method asBukkitCopy  = craftClass.getMethod("asBukkitCopy", nmsISClass);
            Method getCompound   = listClass.getMethod("getCompound", int.class);
            Method getByteMethod = compClass.getMethod("getByte", String.class);

            Inventory inv = Bukkit.createInventory(null, 27,
                    LegacyComponentSerializer.legacySection()
                            .deserialize("§5Enderchest de §e" + offline.getName()));

            for (int i = 0; i < size; i++) {
                Object itemTag = getCompound.invoke(enderList, i);
                // Byte sin signo → slot 0-26
                int slot = ((byte) getByteMethod.invoke(itemTag, "Slot")) & 0xFF;
                if (slot >= 27) continue;

                Object nmsItem = parseOptional.invoke(null, registries, itemTag);
                if (!(boolean) isEmptyMethod.invoke(nmsItem)) {
                    ItemStack bukkit = (ItemStack) asBukkitCopy.invoke(null, nmsItem);
                    inv.setItem(slot, bukkit);
                }
            }

            return inv;

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            plugin.getLogger().warning("Error leyendo playerdata de "
                    + offline.getName() + ": " + cause);
            return null;
        }
    }

    private File getPlayerDataFile(UUID uuid) {
        // El playerdata siempre vive en el mundo principal (índice 0)
        World world = Bukkit.getWorlds().get(0);
        return new File(world.getWorldFolder(), "playerdata/" + uuid + ".dat");
    }

    // -------------------------------------------------------------------------
    // Al cerrar el inventario, si pertenecía a un jugador offline, guardamos
    // los cambios en su archivo .dat de forma asíncrona.
    // Si el jugador se conectó mientras el admin tenía su chest abierto, el
    // guardado se omite para evitar conflictos con la sesión en curso.
    // -------------------------------------------------------------------------
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        OfflineInfo info = openOfflineChests.remove(inv);
        if (info == null) return;

        // Seguridad: no sobrescribir si el jugador ya está online
        if (Bukkit.getPlayer(info.uuid()) != null) {
            if (event.getPlayer() instanceof Player p) {
                p.sendMessage("§e[Aviso] §7" + info.name() + " se conectó; los cambios §cno se guardaron §7para evitar conflictos.");
            }
            return;
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin,
                t -> saveOfflineEnderChest(info, inv,
                        event.getPlayer() instanceof Player p ? p : null));
    }

    // -------------------------------------------------------------------------
    // Guarda los 27 slots del inventario en el tag "EnderItems" del .dat.
    // Carga primero el archivo completo para no perder otros datos del jugador.
    // -------------------------------------------------------------------------
    private void saveOfflineEnderChest(OfflineInfo info, Inventory inv, Player notifyPlayer) {
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

            // Nueva lista de EnderItems
            Object newList = listClass.getDeclaredConstructor().newInstance();
            Method listAdd = List.class.getMethod("add", Object.class);

            for (int slot = 0; slot < 27; slot++) {
                ItemStack bukkit = inv.getItem(slot);
                if (bukkit == null || bukkit.getType().isAir()) continue;

                Object nmsItem = asNMSCopy.invoke(null, bukkit);
                if ((boolean) isEmptyNms.invoke(nmsItem)) continue;

                Object itemTag = saveItem.invoke(nmsItem, registries);
                putByte.invoke(itemTag, "Slot", (byte) slot);
                listAdd.invoke(newList, itemTag);
            }

            // Reemplazar EnderItems en el CompoundTag raíz
            compClass.getMethod("put", String.class, tagClass).invoke(root, "EnderItems", newList);
            // Escribir de vuelta el .dat completo
            nbtIoClass.getMethod("writeCompressed", compClass, Path.class).invoke(null, root, dataFile.toPath());

            if (notifyPlayer != null) {
                notifyPlayer.sendMessage("§a[EnderChest] §7Cambios guardados en el inventario de §e" + info.name() + "§7.");
            }

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            plugin.getLogger().warning("Error guardando enderchest offline de "
                    + info.name() + ": " + cause);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) return List.of();
        if (!player.hasPermission(PERM_OTHERS)) return List.of();

        String partial = args[0].toLowerCase();
        List<String> suggestions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase().startsWith(partial)) {
                suggestions.add(online.getName());
            }
        }
        return suggestions;
    }
}
