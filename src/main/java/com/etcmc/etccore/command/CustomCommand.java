package com.etcmc.etccore.command;

import com.etcmc.etccore.ETCCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 *   [VAR:SET] nombre = expr    → Guarda una variable por jugador
 *   [VAR:ADD] nombre = N       → Suma N a una variable numérica (alias de SET con expr)
 *   [VAR:DEL] nombre           → Elimina una variable
 *   [INPUT] variable;prompt    → Pide texto al jugador (captura siguiente mensaje)
 *   [MENU] nombre              → Abre un menú definido en menus/<nombre>.yml
 *   [PAPI] texto con %placeholder%  → Reemplaza PlaceholderAPI si está instalado
 *
 * === CONDICIONES PARA [IF] ===
 *   permission:nodo            → Tiene el permiso
 *   !permission:nodo           → NO tiene el permiso
 *   world:nombre               → Está en ese mundo
 *   !world:nombre              → NO está en ese mundo
 *   health>5.0                 → Vida mayor a 5 corazones
 *   health<10.0                → Vida menor a 10 corazones
 *   var:nombre=valor           → Variable igual a valor
 *   var:nombre!=valor          → Variable distinta
 *   var:nombre>N               → Variable numérica mayor que N
 *   var:nombre<N               → Variable numérica menor que N
 *
 * === PLACEHOLDERS ===
 *   {player}  {world}  {x} {y} {z}  {args}  {arg0} {arg1}...
 *   {var:nombre}               → Valor de una variable del jugador
 *
 * === ARGS TIPADOS (tab-complete automático) ===
 *   arg-types:
 *     0: player        → completa con jugadores online
 *     1: number        → sin sugerencias (solo número)
 *     2: text          → texto libre
 */
public class CustomCommand extends Command {

    private final ETCCore plugin;
    private boolean disabled = false;

    // Campos mutables para hot-reload
    private List<String> actions;
    private String permission;
    private String noPermMessage;
    private boolean consoleAllowed;
    private long cooldownSeconds;
    private long globalCooldownSeconds;
    private String cooldownMessage;
    private List<String> worldsAllowed;
    private List<String> worldsBlacklist;
    private String worldsMessage;
    private double minHealth;
    private String healthMessage;
    private Map<Integer, String> argTypes; // índice → tipo (player, number, text)

    public CustomCommand(ETCCore plugin, String name, ConfigurationSection sec) {
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
        globalCooldownSeconds = sec.getLong("global-cooldown", 0);
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

        // Arg types para tab-complete
        argTypes = new java.util.LinkedHashMap<>();
        ConfigurationSection argTypesSec = sec.getConfigurationSection("arg-types");
        if (argTypesSec != null) {
            for (String key : argTypesSec.getKeys(false)) {
                try {
                    argTypes.put(Integer.parseInt(key), argTypesSec.getString(key, "text").toLowerCase());
                } catch (NumberFormatException ignored) {}
            }
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

        // Cooldown global
        if (globalCooldownSeconds > 0) {
            var cd = plugin.getCooldownManager();
            if (cd.isOnGlobalCooldown(getName())) {
                long remaining = cd.getGlobalRemainingSeconds(getName());
                String msg = cooldownMessage
                        .replace("{remaining}", String.valueOf(remaining))
                        .replace("{command}", getName());
                sender.sendMessage(comp(msg));
                return true;
            }
            cd.setGlobalCooldown(getName(), globalCooldownSeconds);
        }

        // Cooldown por jugador
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
        // Log execution if CommandLogger is enabled
        plugin.getCommandLogger().log(player, getName(), args);
        return true;
    }

    // -------------------------------------------------------------------------
    // Tab-complete con arg-types
    // -------------------------------------------------------------------------
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (argTypes.isEmpty()) return super.tabComplete(sender, alias, args);

        int index = args.length - 1;
        String type = argTypes.getOrDefault(index, "text");
        String partial = args[index].toLowerCase();

        return switch (type) {
            case "player" -> Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .toList();
            case "number" -> List.of();
            default -> List.of();
        };
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
            if (evalCondition(condition, sender, player, args)) {
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

        // [VAR:SET] nombre = expresión
        if (action.startsWith("[VAR:SET]") || action.startsWith("[VAR:ADD]")) {
            if (player == null) return;
            String body = action.substring(action.indexOf(']') + 1).trim();
            int eq = body.indexOf('=');
            if (eq < 0) return;
            String varName = body.substring(0, eq).trim();
            String expr    = body.substring(eq + 1).trim();
            plugin.getPlayerDataManager().set(player.getUniqueId(), varName, expr);
            return;
        }

        // [VAR:DEL] nombre
        if (action.startsWith("[VAR:DEL]")) {
            if (player == null) return;
            String varName = action.substring("[VAR:DEL]".length()).trim();
            plugin.getPlayerDataManager().remove(player.getUniqueId(), varName);
            return;
        }

        // [INPUT] variable;prompt
        if (action.startsWith("[INPUT]")) {
            if (player == null) return;
            String body = action.substring("[INPUT]".length()).trim();
            String[] parts = body.split(";", 2);
            String varName = parts[0].trim();
            String prompt  = parts.length > 1 ? parts[1].trim() : "&eEscribe el valor:";
            player.sendMessage(comp(prompt));
            plugin.getChatInputManager().expect(player.getUniqueId(), varName);
            return;
        }

        // [MENU] nombre
        if (action.startsWith("[MENU]")) {
            if (player == null) return;
            String menuName = action.substring("[MENU]".length()).trim();
            plugin.getMenuManager().openMenu(player, menuName);
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

        // [PLAYER] cmd
        if (action.startsWith("[PLAYER]")) {
            if (player == null) return;
            String cmd = action.substring("[PLAYER]".length()).trim();
            player.getScheduler().execute(plugin,
                    () -> player.performCommand(cmd), null, 1L);
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

        // [FLY:ON] / [FLY:OFF]
        if (action.equalsIgnoreCase("[FLY:ON]") || action.equalsIgnoreCase("[FLY:OFF]")) {
            if (player == null) return;
            boolean enabled = action.equalsIgnoreCase("[FLY:ON]");
            player.getScheduler().execute(plugin, () -> {
                player.setAllowFlight(enabled);
                player.setFlying(enabled);
            }, null, 1L);
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

        // [MONEY_GIVE:amount]  — gives money via Vault (requires Vault + economy plugin)
        if (action.startsWith("[MONEY_GIVE:")) {
            if (player == null) return;
            int close = action.indexOf(']');
            if (close < 0) return;
            try {
                double amount = Double.parseDouble(action.substring("[MONEY_GIVE:".length(), close));
                plugin.getVaultManager().give(player, amount);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[MONEY_GIVE] Cantidad inválida: " + rawAction);
            }
            return;
        }

        // [MONEY_TAKE:amount]  — takes money; has no effect if insufficient (use [IF money>=X] first)
        if (action.startsWith("[MONEY_TAKE:")) {
            if (player == null) return;
            int close = action.indexOf(']');
            if (close < 0) return;
            try {
                double amount = Double.parseDouble(action.substring("[MONEY_TAKE:".length(), close));
                plugin.getVaultManager().take(player, amount);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[MONEY_TAKE] Cantidad inválida: " + rawAction);
            }
            return;
        }

        // [CLOSE]  — closes the current inventory (used in menus)
        if (action.startsWith("[CLOSE]")) {
            return; // handled by MenuManager's click handler; no-op here
        }

        // [WORLD] nombreMundo  — teleporta al jugador a un mundo (usa ETCWorlds si está instalado)
        // Ejemplo: [WORLD] lobby        |  [WORLD] skyblock_main
        if (action.startsWith("[WORLD]")) {
            if (player == null) return;
            String worldName = action.substring("[WORLD]".length()).trim();
            player.getScheduler().execute(plugin,
                    () -> player.performCommand("world " + worldName), null, 1L);
            return;
        }

        // [SPAWN]  — teleporta al jugador al spawn de su mundo actual
        if (action.equalsIgnoreCase("[SPAWN]")) {
            if (player == null) return;
            player.getScheduler().execute(plugin,
                    () -> player.performCommand("spawn"), null, 1L);
            return;
        }

        // [LOBBY]  — teleporta al jugador al mundo lobby configurado
        if (action.equalsIgnoreCase("[LOBBY]")) {
            if (player == null) return;
            player.getScheduler().execute(plugin,
                    () -> player.performCommand("lobby"), null, 1L);
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
    private boolean evalCondition(String condition, CommandSender sender, Player player, String[] args) {
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
        } else if (condition.startsWith("money")) {
            // money>=X, money<=X, money>X, money<X  (requires Vault)
            result = evalMoneyCondition(condition, player);
        } else if (condition.startsWith("var:")) {
            result = evalVarCondition(condition.substring("var:".length()), player);
        } else if (condition.startsWith("arg:")) {
            // arg:N!=      → args[N] existe y no está vacío
            // arg:N!=valor → args[N] != "valor"
            // arg:N=valor  → args[N] == "valor"
            result = evalArgCondition(condition.substring("arg:".length()), args);
        } else {
            plugin.getLogger().warning("Condición desconocida en [IF]: " + condition);
            result = false;
        }

        return negate != result; // XOR para negación
    }

    /**
     * Evalúa condiciones sobre argumentos: arg:N!=  arg:N!=valor  arg:N=valor
     *   arg:0!=       → args[0] existe y no está vacío
     *   arg:0!=Emma   → args[0] != "Emma" (ignorando mayúsculas)
     *   arg:0=Emma    → args[0] == "Emma" (ignorando mayúsculas)
     */
    private boolean evalArgCondition(String expr, String[] args) {
        if (expr.contains("!=")) {
            String[] p = expr.split("!=", 2);
            int idx;
            try { idx = Integer.parseInt(p[0].trim()); } catch (NumberFormatException e) { return false; }
            String argVal = idx < args.length ? args[idx] : "";
            String expected = p[1].trim();
            // arg:0!=  sin valor → comprueba que existe y no está vacío
            return expected.isEmpty() ? !argVal.isEmpty() : !argVal.equalsIgnoreCase(expected);
        } else if (expr.contains("=")) {
            String[] p = expr.split("=", 2);
            int idx;
            try { idx = Integer.parseInt(p[0].trim()); } catch (NumberFormatException e) { return false; }
            String argVal = idx < args.length ? args[idx] : "";
            return argVal.equalsIgnoreCase(p[1].trim());
        }
        // arg:0  solo → verdadero si existe y no está vacío
        try {
            int idx = Integer.parseInt(expr.trim());
            return idx < args.length && !args[idx].isEmpty();
        } catch (NumberFormatException e) { return false; }
    }

    /** Evaluates Vault economy conditions: money>=X, money<=X, money>X, money<X */
    private boolean evalMoneyCondition(String condition, Player player) {
        if (player == null) return false;
        var vault = plugin.getVaultManager();
        if (!vault.isEnabled()) return false;
        double balance = vault.getBalance(player);
        try {
            if (condition.contains(">=")) {
                return balance >= Double.parseDouble(condition.split(">=", 2)[1].trim());
            } else if (condition.contains("<=")) {
                return balance <= Double.parseDouble(condition.split("<=", 2)[1].trim());
            } else if (condition.contains(">")) {
                return balance > Double.parseDouble(condition.split(">", 2)[1].trim());
            } else if (condition.contains("<")) {
                return balance < Double.parseDouble(condition.split("<", 2)[1].trim());
            } else if (condition.contains("=")) {
                return balance == Double.parseDouble(condition.split("=", 2)[1].trim());
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[IF money] Valor inválido: " + condition);
        }
        return false;
    }

    /** Evalúa condiciones sobre variables: nombre=val, nombre!=val, nombre>N, nombre<N */
    private boolean evalVarCondition(String expr, Player player) {
        if (player == null) return false;
        String val;

        if (expr.contains("!=")) {
            String[] p = expr.split("!=", 2);
            val = plugin.getPlayerDataManager().get(player.getUniqueId(), p[0].trim());
            return !val.equalsIgnoreCase(p[1].trim());
        } else if (expr.contains(">=")) {
            String[] p = expr.split(">=", 2);
            val = plugin.getPlayerDataManager().get(player.getUniqueId(), p[0].trim());
            try { return Double.parseDouble(val) >= Double.parseDouble(p[1].trim()); }
            catch (NumberFormatException e) { return false; }
        } else if (expr.contains("<=")) {
            String[] p = expr.split("<=", 2);
            val = plugin.getPlayerDataManager().get(player.getUniqueId(), p[0].trim());
            try { return Double.parseDouble(val) <= Double.parseDouble(p[1].trim()); }
            catch (NumberFormatException e) { return false; }
        } else if (expr.contains(">")) {
            String[] p = expr.split(">", 2);
            val = plugin.getPlayerDataManager().get(player.getUniqueId(), p[0].trim());
            try { return Double.parseDouble(val) > Double.parseDouble(p[1].trim()); }
            catch (NumberFormatException e) { return false; }
        } else if (expr.contains("<")) {
            String[] p = expr.split("<", 2);
            val = plugin.getPlayerDataManager().get(player.getUniqueId(), p[0].trim());
            try { return Double.parseDouble(val) < Double.parseDouble(p[1].trim()); }
            catch (NumberFormatException e) { return false; }
        } else if (expr.contains("=")) {
            String[] p = expr.split("=", 2);
            val = plugin.getPlayerDataManager().get(player.getUniqueId(), p[0].trim());
            return val.equalsIgnoreCase(p[1].trim());
        }
        return false;
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

            // Variables de jugador: {var:nombre}
            int idx;
            while ((idx = r.indexOf("{var:")) >= 0) {
                int end = r.indexOf("}", idx);
                if (end < 0) break;
                String varName = r.substring(idx + 5, end);
                String varVal  = plugin.getPlayerDataManager().get(player.getUniqueId(), varName);
                r = r.substring(0, idx) + varVal + r.substring(end + 1);
            }

            // Vault balance placeholders (requires Vault + economy plugin)
            if (plugin.getVaultManager().isEnabled()) {
                double bal = plugin.getVaultManager().getBalance(player);
                r = r.replace("{balance}",     String.valueOf((long) bal));
                r = r.replace("{balance_fmt}", plugin.getVaultManager().format(bal));
            }

            // PlaceholderAPI (solo si está instalado)
            r = applyPapi(r, player);

        } else {
            r = r.replace("{player}", sender.getName());
        }
        r = r.replace("{args}", args.length > 0 ? String.join(" ", args) : "");
        for (int i = 0; i < args.length; i++) {
            r = r.replace("{arg" + i + "}", args[i]);
        }
        return r;
    }

    /** Aplica PlaceholderAPI si el plugin está habilitado en el servidor. */
    private String applyPapi(String text, Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) papiClass
                    .getMethod("setPlaceholders", Player.class, String.class)
                    .invoke(null, player, text);
        } catch (Exception e) {
            return text;
        }
    }

    // -------------------------------------------------------------------------
    // Método estático para que otros sistemas (MenuManager) ejecuten acciones
    // sin necesidad de crear un comando real.
    // -------------------------------------------------------------------------
    public static void fireAction(ETCCore plugin, Player player,
                                  String action, String[] args) {
        ActionEngine.run(plugin, player, action, args);
    }

    /**
     * Motor de acciones expuesto estáticamente para reutilización.
     * Se usa desde fireAction (MenuManager) y desde processAction.
     */
    static class ActionEngine {
        static void run(ETCCore plugin, Player player,
                        String action, String[] args) {
            // Delegamos a una instancia mínima temporal que solo usa processAction
            new _Runner(plugin).exec(player, action, args);
        }

        private static class _Runner {
            private final ETCCore plugin;
            _Runner(ETCCore p) { this.plugin = p; }

            void exec(Player player, String action, String[] args) {
                // Reusa la implementación de processAction de CustomCommand
                // creando un CustomCommand con YamlConfiguration mínima
                org.bukkit.configuration.file.YamlConfiguration sec =
                        new org.bukkit.configuration.file.YamlConfiguration();
                sec.set("actions", List.of());
                CustomCommand tmp = new CustomCommand(plugin, "__dispatch__", sec);
                tmp.processAction(player, player, action, args);
            }
        }
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
