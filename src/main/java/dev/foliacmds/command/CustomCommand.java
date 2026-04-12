package dev.foliacmds.command;

import dev.foliacmds.FoliaCustomCommands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Comando personalizado cargado desde un archivo YAML en commands/.
 *
 * === PREFIJOS DE ACCIÓN ===
 *   (sin prefijo)              → El jugador ejecuta el comando
 *   [CONSOLE] cmd              → La consola ejecuta el comando
 *   [MESSAGE] texto            → Mensaje al jugador (&colores)
 *   [BROADCAST] texto          → Mensaje a todos los jugadores online
 *   [ACTIONBAR] texto          → Barra de acción al jugador
 *   [TITLE] titulo;subtitulo   → Título en pantalla (tiempos por defecto)
 *   [TITLE:fadein:stay:fadeout] → Título con tiempos personalizados (ticks)
 *   [SOUND] NOMBRE_SONIDO      → Reproduce un sonido al jugador
 *   [SOUND:vol:pitch] NOMBRE   → Con volumen y pitch personalizados
 *   [DELAY:ticks] <acción>     → Ejecuta cualquier acción con retraso
 *   [CHANCE:porcentaje] <acción> → Ejecuta con probabilidad (0-100)
 *   [IF condición] <acción>    → Ejecuta si se cumple la condición
 *
 * === CONDICIONES PARA [IF] ===
 *   permission:nodo            → Tiene el permiso
 *   !permission:nodo           → NO tiene el permiso
 *   world:nombre               → Está en ese mundo
 *   !world:nombre              → NO está en ese mundo
 *   health>5.0                 → Vida mayor a 5 corazones
 *   health<10.0                → Vida menor a 10 corazones
 *
 * === PLACEHOLDERS ===
 *   {player}  {world}  {x} {y} {z}  {args}  {arg0} {arg1}...
 */
public class CustomCommand extends Command {

    private final FoliaCustomCommands plugin;
    private boolean disabled = false;

    // Campos mutables para hot-reload
    private List<String> actions;
    private String permission;
    private String noPermMessage;
    private boolean consoleAllowed;
    private long cooldownSeconds;
    private String cooldownMessage;
    private List<String> worldsAllowed;
    private List<String> worldsBlacklist;
    private String worldsMessage;
    private double minHealth;
    private String healthMessage;

    public CustomCommand(FoliaCustomCommands plugin, String name, ConfigurationSection sec) {
        super(name);
        this.plugin = plugin;
        applySection(sec);
    }

    // -------------------------------------------------------------------------
    // Carga / recarga de configuración desde una sección YAML
    // -------------------------------------------------------------------------
    public void applySection(ConfigurationSection sec) {
        permission     = sec.getString("permission", "");
        noPermMessage  = sec.getString("no-permission-message", "&cNo tienes permiso.");
        consoleAllowed = sec.getBoolean("console-allowed", false);
        actions        = new ArrayList<>(sec.getStringList("actions"));
        cooldownSeconds = sec.getLong("cooldown", 0);
        cooldownMessage = sec.getString("cooldown-message",
                "&cEspera &e{remaining}s &cpara volver a usar este comando.");

        ConfigurationSection cond = sec.getConfigurationSection("conditions");
        if (cond != null) {
            worldsAllowed   = cond.getStringList("worlds");
            worldsBlacklist = cond.getStringList("worlds-blacklist");
            worldsMessage   = cond.getString("worlds-message", "&cNo puedes usar esto en este mundo.");
            minHealth       = cond.getDouble("min-health", 0.0);
            healthMessage   = cond.getString("health-message", "&cNecesitas más salud para usar esto.");
        } else {
            worldsAllowed   = new ArrayList<>();
            worldsBlacklist = new ArrayList<>();
            worldsMessage   = "&cNo puedes usar esto en este mundo.";
            minHealth       = 0.0;
            healthMessage   = "&cNecesitas más salud para usar esto.";
        }

        String desc = sec.getString("description", "");
        setDescription(desc);
        if (!permission.isEmpty()) setPermission(permission);

        List<String> aliases = sec.getStringList("aliases");
        if (!aliases.isEmpty()) setAliases(aliases);
    }

    // -------------------------------------------------------------------------
    // Ejecución principal
    // -------------------------------------------------------------------------
    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (disabled) {
            sender.sendMessage(comp("&cEste comando ha sido deshabilitado."));
            return true;
        }
        if (!consoleAllowed && !(sender instanceof Player)) {
            sender.sendMessage(comp("&cEste comando solo puede ser ejecutado por jugadores."));
            return true;
        }
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(comp(noPermMessage));
            return true;
        }

        Player player = (sender instanceof Player p) ? p : null;

        // Condiciones de mundo y salud
        if (player != null) {
            String world = player.getWorld().getName();
            if (!worldsAllowed.isEmpty()
                    && worldsAllowed.stream().noneMatch(w -> w.equalsIgnoreCase(world))) {
                sender.sendMessage(comp(worldsMessage));
                return true;
            }
            if (worldsBlacklist.stream().anyMatch(w -> w.equalsIgnoreCase(world))) {
                sender.sendMessage(comp(worldsMessage));
                return true;
            }
            if (minHealth > 0 && player.getHealth() < minHealth) {
                sender.sendMessage(comp(healthMessage));
                return true;
            }
        }

        // Cooldown
        if (cooldownSeconds > 0 && player != null) {
            var cd = plugin.getCooldownManager();
            if (cd.isOnCooldown(player.getUniqueId(), getName())) {
                long remaining = cd.getRemainingSeconds(player.getUniqueId(), getName());
                String msg = cooldownMessage
                        .replace("{remaining}", String.valueOf(remaining))
                        .replace("{command}", getName());
                sender.sendMessage(comp(msg));
                return true;
            }
            cd.setCooldown(player.getUniqueId(), getName(), cooldownSeconds);
        }

        for (String rawAction : actions) {
            processAction(sender, player, rawAction, args);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Motor de acciones (recursivo para DELAY, CHANCE, IF)
    // -------------------------------------------------------------------------
    private void processAction(CommandSender sender, Player player,
                               String rawAction, String[] args) {
        String action = applyPlaceholders(rawAction, sender, player, args);

        // [IF condición] <acción>
        if (action.startsWith("[IF ")) {
            int close = action.indexOf(']');
            if (close < 0) return;
            String condition = action.substring(4, close).trim();
            String remaining = action.substring(close + 1).trim();
            if (evalCondition(condition, sender, player)) {
                processAction(sender, player, remaining, args);
            }
            return;
        }

        // [CHANCE:X] <acción>
        if (action.startsWith("[CHANCE:")) {
            int close = action.indexOf(']');
            if (close < 0) return;
            try {
                double chance = Double.parseDouble(action.substring("[CHANCE:".length(), close));
                String remaining = action.substring(close + 1).trim();
                if (Math.random() * 100.0 < chance) {
                    processAction(sender, player, remaining, args);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Probabilidad inválida: " + rawAction);
            }
            return;
        }

        // [DELAY:ticks] <acción>
        if (action.startsWith("[DELAY:")) {
            int close = action.indexOf(']');
            if (close < 0) return;
            try {
                long ticks = Long.parseLong(action.substring("[DELAY:".length(), close));
                String remaining = action.substring(close + 1).trim();
                if (player != null) {
                    player.getScheduler().execute(plugin,
                            () -> processAction(sender, player, remaining, args), null, ticks);
                } else {
                    plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin,
                            t -> processAction(sender, null, remaining, args), ticks);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Delay inválido: " + rawAction);
            }
            return;
        }

        // [CONSOLE] cmd
        if (action.startsWith("[CONSOLE]")) {
            String cmd = action.substring("[CONSOLE]".length()).trim();
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                    plugin.getServer().dispatchCommand(
                            plugin.getServer().getConsoleSender(), cmd));
            return;
        }

        // [MESSAGE] texto
        if (action.startsWith("[MESSAGE]")) {
            sender.sendMessage(comp(action.substring("[MESSAGE]".length()).trim()));
            return;
        }

        // [BROADCAST] texto
        if (action.startsWith("[BROADCAST]")) {
            Component c = comp(action.substring("[BROADCAST]".length()).trim());
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                    plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(c)));
            return;
        }

        // [ACTIONBAR] texto
        if (action.startsWith("[ACTIONBAR]")) {
            if (player != null) {
                Component c = comp(action.substring("[ACTIONBAR]".length()).trim());
                player.getScheduler().execute(plugin,
                        () -> player.sendActionBar(c), null, 1L);
            }
            return;
        }

        // [TITLE] título;subtítulo
        // [TITLE:fadeIn:stay:fadeOut] título;subtítulo  (tiempos en ticks)
        if (action.startsWith("[TITLE]") || action.startsWith("[TITLE:")) {
            int close = action.indexOf(']');
            if (close < 0) return;
            int fadeIn = 10, stay = 70, fadeOut = 20;
            if (action.startsWith("[TITLE:")) {
                String[] t = action.substring("[TITLE:".length(), close).split(":");
                try {
                    if (t.length >= 1 && !t[0].isEmpty()) fadeIn  = Integer.parseInt(t[0]);
                    if (t.length >= 2)                     stay    = Integer.parseInt(t[1]);
                    if (t.length >= 3)                     fadeOut = Integer.parseInt(t[2]);
                } catch (NumberFormatException ignored) {}
            }
            String content = action.substring(close + 1).trim();
            String[] parts = content.split(";", 2);
            Component titleComp    = comp(parts[0]);
            Component subtitleComp = parts.length > 1 ? comp(parts[1]) : Component.empty();
            if (player != null) {
                final int fi = fadeIn, st = stay, fo = fadeOut;
                Title title = Title.title(titleComp, subtitleComp,
                        Title.Times.times(Duration.ofMillis(fi * 50L),
                                Duration.ofMillis(st * 50L),
                                Duration.ofMillis(fo * 50L)));
                player.getScheduler().execute(plugin,
                        () -> player.showTitle(title), null, 1L);
            }
            return;
        }

        // [SOUND] NOMBRE
        // [SOUND:vol:pitch] NOMBRE
        if (action.startsWith("[SOUND]") || action.startsWith("[SOUND:")) {
            int close = action.indexOf(']');
            if (close < 0) return;
            float volume = 1.0f, pitch = 1.0f;
            if (action.startsWith("[SOUND:")) {
                String[] p = action.substring("[SOUND:".length(), close).split(":");
                try {
                    if (p.length >= 1) volume = Float.parseFloat(p[0]);
                    if (p.length >= 2) pitch  = Float.parseFloat(p[1]);
                } catch (NumberFormatException ignored) {}
            }
            String soundName = action.substring(close + 1).trim();
            if (player != null) {
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase().replace(".", "_"));
                    float finalVol = volume, finalPitch = pitch;
                    player.getScheduler().execute(plugin, () ->
                            player.playSound(player.getLocation(), sound, finalVol, finalPitch),
                            null, 1L);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Sonido inválido: " + soundName);
                }
            }
            return;
        }

        // Sin prefijo → el jugador ejecuta el comando
        if (player != null) {
            player.getScheduler().execute(plugin,
                    () -> player.performCommand(action), null, 1L);
        } else {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                    plugin.getServer().dispatchCommand(
                            plugin.getServer().getConsoleSender(), action));
        }
    }

    // -------------------------------------------------------------------------
    // Evaluación de condiciones para [IF]
    // -------------------------------------------------------------------------
    private boolean evalCondition(String condition, CommandSender sender, Player player) {
        boolean negate = condition.startsWith("!");
        if (negate) condition = condition.substring(1).trim();

        boolean result;
        if (condition.startsWith("permission:")) {
            result = sender.hasPermission(condition.substring("permission:".length()));
        } else if (condition.startsWith("world:")) {
            String w = condition.substring("world:".length());
            result = player != null && player.getWorld().getName().equalsIgnoreCase(w);
        } else if (condition.startsWith("health>")) {
            try {
                double hp = Double.parseDouble(condition.substring("health>".length()));
                result = player != null && player.getHealth() > hp;
            } catch (NumberFormatException e) { result = false; }
        } else if (condition.startsWith("health<")) {
            try {
                double hp = Double.parseDouble(condition.substring("health<".length()));
                result = player != null && player.getHealth() < hp;
            } catch (NumberFormatException e) { result = false; }
        } else {
            plugin.getLogger().warning("Condición desconocida en [IF]: " + condition);
            result = false;
        }

        return negate != result; // XOR para negación
    }

    // -------------------------------------------------------------------------
    // Placeholders
    // -------------------------------------------------------------------------
    private String applyPlaceholders(String text, CommandSender sender,
                                     Player player, String[] args) {
        String r = text;
        if (player != null) {
            r = r.replace("{player}", player.getName());
            r = r.replace("{world}",  player.getWorld().getName());
            r = r.replace("{x}", String.valueOf((int) player.getLocation().getX()));
            r = r.replace("{y}", String.valueOf((int) player.getLocation().getY()));
            r = r.replace("{z}", String.valueOf((int) player.getLocation().getZ()));
        } else {
            r = r.replace("{player}", sender.getName());
        }
        r = r.replace("{args}", args.length > 0 ? String.join(" ", args) : "");
        for (int i = 0; i < args.length; i++) {
            r = r.replace("{arg" + i + "}", args[i]);
        }
        return r;
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------
    private static Component comp(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
