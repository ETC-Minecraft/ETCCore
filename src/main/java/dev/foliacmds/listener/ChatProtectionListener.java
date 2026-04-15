package dev.foliacmds.listener;

import dev.foliacmds.FoliaCustomCommands;
import dev.foliacmds.manager.MuteManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Controla quién puede hablar en el chat y da formato con LuckPerms + PAPI.
 *
 *   1. Permiso LP:    etccore.chat default true → denegar silencia vía LP.
 *   2. Mute manual:   /mute <jugador>
 *   3. Formato:       cargado desde chat-format.yml, recargable con /etccore reload.
 *                     Soporta {prefix}, {player}, {suffix}, {world}, {message} y %papi%.
 */
public class ChatProtectionListener implements Listener {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private static final String DEFAULT_FORMAT = "{prefix}{player}{suffix}&7: &f{message}";

    private final FoliaCustomCommands plugin;
    private final MuteManager         muteManager;
    private final File                formatFile;

    private String    format;
    private LuckPerms luckPerms;

    public ChatProtectionListener(FoliaCustomCommands plugin) {
        this.plugin      = plugin;
        this.muteManager = plugin.getMuteManager();
        this.formatFile  = new File(plugin.getDataFolder(), "chat-format.yml");

        tryLoadLP();
        ensureDefault();
        loadFormat();
    }

    /** Llamado desde ReloadCommand para recargar el formato en caliente. */
    public void reload() {
        loadFormat();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatCheck(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("etccore.chat")) {
            event.setCancelled(true);
            player.sendMessage(LEGACY.deserialize("&cNo tienes permiso para chatear en este servidor."));
            return;
        }

        if (!muteManager.isMuted(player.getUniqueId())) return;

        MuteManager.MuteEntry entry = muteManager.getEntry(player.getUniqueId());
        event.setCancelled(true);
        player.sendMessage(LEGACY.deserialize("&c&lEstás silenciado. &7No puedes enviar mensajes al chat."));
        player.sendMessage(LEGACY.deserialize("&7Razón: &f" + entry.reason()));
        if (entry.isPermanent()) {
            player.sendMessage(LEGACY.deserialize(
                    "&7Duración: &fPermanente &8— hasta que un administrador lo decida."));
        } else {
            player.sendMessage(LEGACY.deserialize(
                    "&7Tiempo restante: &f" + entry.getRemainingFormatted()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChatFormat(AsyncChatEvent event) {
        if (luckPerms == null && !isPapiEnabled()) return;

        event.renderer((source, displayName, message, viewer) -> {
            String prefix = "";
            String suffix = "";

            if (luckPerms != null) {
                User user = luckPerms.getUserManager().getUser(source.getUniqueId());
                if (user != null) {
                    prefix = Objects.requireNonNullElse(
                            user.getCachedData().getMetaData().getPrefix(), "");
                    suffix = Objects.requireNonNullElse(
                            user.getCachedData().getMetaData().getSuffix(), "");
                }
            }

            String msgStr    = LEGACY.serialize(message);
            String worldName = source.getWorld().getName();

            String line = format
                    .replace("{prefix}",  prefix)
                    .replace("{suffix}",  suffix)
                    .replace("{player}",  source.getName())
                    .replace("{world}",   worldName)
                    .replace("{message}", msgStr);

            line = applyPapi(line, source);
            return LEGACY.deserialize(line);
        });
    }

    private void loadFormat() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(formatFile);
        format = cfg.getString("format", DEFAULT_FORMAT);
    }

    private void ensureDefault() {
        if (!formatFile.exists()) {
            plugin.saveResource("chat-format.yml", false);
        }
    }

    private void tryLoadLP() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
            }
        } catch (IllegalStateException ignored) {}
    }

    private static boolean isPapiEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private static String applyPapi(String text, Player player) {
        if (!isPapiEnabled()) return text;
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
            return (String) m.invoke(null, player, text);
        } catch (Exception e) {
            return text;
        }
    }
}
