package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;
import dev.foliacmds.command.CustomCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona menús de inventario (GUIs) definidos en archivos YAML bajo menus/.
 *
 * Formato de un archivo de menú:
 *
 *   title: "&8Panel de jugador"
 *   rows: 3                       # 1–6
 *   items:
 *     13:                         # slot (0-based)
 *       material: COMPASS
 *       name: "&aIr al spawn"
 *       lore:
 *         - "&7Haz clic para teleportarte"
 *       glow: true                # añade brillo sin enchant visible
 *       close-on-click: true      # cierra el inventario al hacer clic (default: true)
 *       actions:
 *         - "[CONSOLE] spawn {player}"
 *     22:
 *       material: BARRIER
 *       name: "&cCerrar"
 *       actions:
 *         - "[CLOSE]"
 */
public class MenuManager implements Listener {

    private final FoliaCustomCommands plugin;

    // nombre → definición de menú
    private final Map<String, MenuDefinition> menus = new ConcurrentHashMap<>();
    // inventario abierto → (jugador UUID, nombre del menú)
    private final Map<Inventory, OpenMenu> openMenus = new ConcurrentHashMap<>();
    // nodos de permiso registrados dinámicamente
    private final Set<String> dynPermissions = new HashSet<>();

    public MenuManager(FoliaCustomCommands plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Carga / recarga de archivos
    // -------------------------------------------------------------------------
    public void loadMenus() {
        // Limpiar permisos registrados previamente (recarga)
        for (String node : dynPermissions) Bukkit.getPluginManager().removePermission(node);
        dynPermissions.clear();
        menus.clear();

        File dir = new File(plugin.getDataFolder(), "menus");
        if (!dir.exists()) { dir.mkdirs(); return; }

        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".yml", "").toLowerCase();
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            MenuDefinition def = parseMenu(name, cfg);
            menus.put(name, def);
            registerDynPermission("fccmds.menus." + name);
        }
        plugin.getLogger().info("Menús cargados: " + menus.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Apertura de un menú
    // ─────────────────────────────────────────────────────────────────────────
    public boolean openMenu(Player player, String name) {
        MenuDefinition def = menus.get(name.toLowerCase());
        if (def == null) return false;

        // Comprobar permiso del menú si está definido
        if (!def.permission().isEmpty() && !player.hasPermission(def.permission())) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&cNo tienes permiso para abrir este menú."));
            return false;
        }

        // ── Geyser / Floodgate: send a native Bedrock form instead of a chest GUI ──
        if (isBedrockPlayer(player)) {
            openFloodgateForm(player, name, def);
            return true;
        }

        // ── Java: standard inventory GUI ─────────────────────────────────────
        Inventory inv = Bukkit.createInventory(null, def.rows * 9,
                LegacyComponentSerializer.legacyAmpersand().deserialize(def.title));

        for (Map.Entry<Integer, MenuItem> entry : def.items.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= def.rows * 9) continue;
            inv.setItem(slot, buildItem(entry.getValue(), player));
        }

        openMenus.put(inv, new OpenMenu(player.getUniqueId(), name.toLowerCase()));
        player.getScheduler().run(plugin,
                t -> player.openInventory(inv),
                null);
        return true;
    }

    /**
     * Returns true if the player is connected via Geyser/Floodgate (Bedrock Edition).
     * Requires the Floodgate plugin; returns false if not installed.
     */
    private boolean isBedrockPlayer(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (boolean) apiClass.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(api, player.getUniqueId());
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Opens a native Bedrock SimpleForm for Floodgate players.
     * Buttons are generated from the menu items, in slot order.
     * Clicking a button executes the corresponding item's actions.
     */
    private void openFloodgateForm(Player player, String menuName, MenuDefinition def) {
        try {
            // Collect buttons in slot order
            java.util.List<MenuItem> buttons = def.items.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(java.util.Map.Entry::getValue)
                    .filter(item -> !item.actions().isEmpty())
                    .toList();

            Class<?> formClass   = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer");
            Class<?> apiClass    = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Class<?> simpleForm  = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Class<?> builderClass = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder");

            Object builder = simpleForm.getMethod("builder").invoke(null);
            // Strip color codes for Bedrock title
            String cleanTitle = def.title().replaceAll("&[0-9a-fk-or]", "");
            builderClass.getMethod("title", String.class).invoke(builder, cleanTitle);

            for (MenuItem item : buttons) {
                String label = LegacyComponentSerializer.legacyAmpersand()
                        .serialize(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(item.name()))
                        .replaceAll("\u00a7[0-9a-fk-or]", "");
                builderClass.getMethod("button", String.class).invoke(builder, label);
            }

            // Response handler
            java.util.List<MenuItem> finalButtons = buttons;
            builderClass.getMethod("validResultHandler",
                            java.util.function.BiConsumer.class)
                    .invoke(builder, (java.util.function.BiConsumer<?, ?>) (form, response) -> {
                        try {
                            int idx = (int) response.getClass().getMethod("clickedButtonId")
                                    .invoke(response);
                            if (idx < 0 || idx >= finalButtons.size()) return;
                            MenuItem item = finalButtons.get(idx);
                            for (String action : item.actions()) {
                                plugin.getCommandManager().dispatchAction(player, action, new String[0]);
                            }
                        } catch (Exception ignored) {}
                    });

            Object form = builderClass.getMethod("build").invoke(builder);
            Object api  = apiClass.getMethod("getInstance").invoke(null);
            apiClass.getMethod("sendForm",
                    java.util.UUID.class, Class.forName("org.geysermc.cumulus.form.Form"))
                    .invoke(api, player.getUniqueId(), form);

        } catch (Exception e) {
            // Floodgate API not available or incompatible version — fall back to chest GUI
            plugin.getLogger().warning("[MenuManager] No se pudo abrir form Floodgate: "
                    + e.getMessage() + ". Usando GUI de inventario.");
            // Fallback: open chest inventory
            Inventory inv = Bukkit.createInventory(null, def.rows * 9,
                    LegacyComponentSerializer.legacyAmpersand().deserialize(def.title));
            for (Map.Entry<Integer, MenuItem> entry : def.items.entrySet()) {
                int slot = entry.getKey();
                if (slot < 0 || slot >= def.rows * 9) continue;
                inv.setItem(slot, buildItem(entry.getValue(), player));
            }
            openMenus.put(inv, new OpenMenu(player.getUniqueId(), menuName.toLowerCase()));
            player.getScheduler().run(plugin, t -> player.openInventory(inv), null);
        }
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        OpenMenu open = openMenus.get(inv);
        if (open == null) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = event.getSlot();
        MenuDefinition def = menus.get(open.menuName());
        if (def == null) return;

        MenuItem item = def.items.get(slot);
        if (item == null) return;

        if (item.closeOnClick) {
            player.getScheduler().run(plugin, t -> player.closeInventory(), null);
        }

        // Ejecutar actions usando el motor de CustomCommand
        for (String action : item.actions) {
            if (action.equalsIgnoreCase("[CLOSE]")) {
                player.getScheduler().run(plugin, t -> player.closeInventory(), null);
                continue;
            }
            plugin.getCommandManager().dispatchAction(player, action, new String[0]);
        }
    }

    // -------------------------------------------------------------------------
    // Al cerrar, limpiar el mapa
    // -------------------------------------------------------------------------
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        openMenus.remove(event.getInventory());
    }

    // -------------------------------------------------------------------------
    // Helpers de construcción
    // -------------------------------------------------------------------------
    private ItemStack buildItem(MenuItem item, Player player) {
        Material mat = Material.matchMaterial(item.material.toUpperCase().replace(" ", "_"));
        if (mat == null) mat = Material.STONE;

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (item.name != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(applyPlayerPlaceholders(item.name, player)));
        }
        if (!item.lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> loreComponents = item.lore.stream()
                    .map(l -> (net.kyori.adventure.text.Component) LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(applyPlayerPlaceholders(l, player)))
                    .toList();
            meta.lore(loreComponents);
        }
        if (item.glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (item.customModelData > 0) {
            meta.setCustomModelData(item.customModelData);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private MenuDefinition parseMenu(String name, YamlConfiguration cfg) {
        String title = cfg.getString("title", name);
        int rows = Math.max(1, Math.min(6, cfg.getInt("rows", 3)));
        String permission = cfg.getString("permission", "");
        Map<Integer, MenuItem> items = new LinkedHashMap<>();

        ConfigurationSection itemsSec = cfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ConfigurationSection s = itemsSec.getConfigurationSection(key);
                    if (s == null) continue;
                    MenuItem item = new MenuItem(
                            s.getString("material", "STONE"),
                            s.getString("name", ""),
                            s.getStringList("lore"),
                            s.getBoolean("glow", false),
                            s.getBoolean("close-on-click", true),
                            s.getInt("custom-model-data", 0),
                            s.getStringList("actions")
                    );
                    items.put(slot, item);
                } catch (NumberFormatException ignored) {}
            }
        }
        return new MenuDefinition(title, rows, items, permission);
    }

    private String applyPlayerPlaceholders(String text, Player player) {
        return text
                .replace("{player}", player.getName())
                .replace("{world}", player.getWorld().getName());
    }

    public boolean menuExists(String name) {
        return menus.containsKey(name.toLowerCase());
    }

    public Set<String> getMenuNames() {
        return Collections.unmodifiableSet(menus.keySet());
    }

    // -------------------------------------------------------------------------
    // Gestión de permisos dinámicos
    // -------------------------------------------------------------------------
    private void registerDynPermission(String node) {
        if (dynPermissions.contains(node)) return;
        try {
            Bukkit.getPluginManager().addPermission(
                    new Permission(node, PermissionDefault.TRUE));
            dynPermissions.add(node);
        } catch (IllegalArgumentException ignored) {
            dynPermissions.add(node);
        }
    }

    // -------------------------------------------------------------------------
    // Records internos
    // -------------------------------------------------------------------------
    private record MenuDefinition(String title, int rows, Map<Integer, MenuItem> items, String permission) {}

    private record MenuItem(
            String material,
            String name,
            List<String> lore,
            boolean glow,
            boolean closeOnClick,
            int customModelData,
            List<String> actions
    ) {}

    private record OpenMenu(UUID playerUuid, String menuName) {}
}
