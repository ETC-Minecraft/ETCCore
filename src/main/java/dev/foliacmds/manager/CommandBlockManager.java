package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carga las reglas de bloqueo de comandos desde blocked-commands.yml y
 * comprueba si un jugador (por sus grupos LuckPerms) puede ejecutar un comando.
 *
 * Flujo de comprobación:
 *   1. ¿Tiene etccore.cmdblock.bypass? → siempre permitido.
 *   2. ¿Alguna regla coincide con el comando Y con alguno de los grupos del jugador? → bloqueado.
 *   3. En caso contrario → permitido.
 *
 * Wildcards: añade * al final del patrón para prefijo libre.
 *   "tp*" → coincide con tp, tpa, tpaccept, tphere…
 *
 * Sin LuckPerms: las reglas de grupo no se aplican (se registra un aviso).
 */
public class CommandBlockManager {

    private record BlockRule(List<String> patterns, Set<String> groups, String message) {}

    private final FoliaCustomCommands plugin;
    private final File                configFile;
    private final List<BlockRule>     rules = new ArrayList<>();
    private String                    defaultMessage;
    private LuckPerms                 luckPerms;

    // ── Allowlist mode ────────────────────────────────────────────────────────
    private boolean     allowlistEnabled = false;
    private Set<String> allowlistGroups  = new HashSet<>();
    private String      allowlistMessage;

    public CommandBlockManager(FoliaCustomCommands plugin) {
        this.plugin     = plugin;
        this.configFile = new File(plugin.getDataFolder(), "blocked-commands.yml");
        tryLoadLP();
        ensureDefault();
        load();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void reload() {
        load();
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Devuelve el mensaje de bloqueo si el jugador no puede ejecutar el comando,
     * o {@code null} si el comando está permitido.
     */
    public String getBlockMessage(Player player, String rawCommand) {
        if (player.hasPermission("etccore.cmdblock.bypass")) return null;
        String cmd = normalize(rawCommand);
        Set<String> groups = getPlayerGroups(player);

        // ── Modo allowlist ────────────────────────────────────────────────────
        // Si está activo y el jugador pertenece a algún grupo allowlist,
        // solo se permite el comando si tiene etccore.allow.<comando> en LP.
        if (allowlistEnabled && matchesAnyGroup(groups, allowlistGroups)) {
            if (!player.hasPermission("etccore.allow." + cmd)) {
                return allowlistMessage;
            }
            return null; // tiene el permiso → permitido
        }

        // ── Modo blocklist (comportamiento original) ──────────────────────────
        for (BlockRule rule : rules) {
            if (matchesAnyGroup(groups, rule.groups()) && matchesAnyPattern(cmd, rule.patterns())) {
                return rule.message();
            }
        }
        return null;
    }

    /**
     * Indica si el comando debe ocultarse del tab-complete para este jugador.
     */
    public boolean isHidden(Player player, String rawCommand) {
        return getBlockMessage(player, rawCommand) != null;
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void load() {
        rules.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        defaultMessage = cfg.getString("default-message", "&cNo tienes permiso para usar ese comando.");

        // Allowlist
        allowlistEnabled = cfg.getBoolean("allowlist.enabled", false);
        allowlistMessage = cfg.getString("allowlist.message", defaultMessage);
        allowlistGroups  = new HashSet<>();
        List<?> alGroups = cfg.getList("allowlist.groups", List.of());
        for (Object g : alGroups) allowlistGroups.add(g.toString().toLowerCase().trim());

        List<?> rawList = cfg.getList("rules", List.of());
        for (Object obj : rawList) {
            if (!(obj instanceof Map<?, ?> map)) continue;

            // commands
            Object cmdsObj = map.get("commands");
            List<String> patterns = new ArrayList<>();
            if (cmdsObj instanceof List<?> cmdList) {
                for (Object c : cmdList) {
                    patterns.add(normalize(c.toString()));
                }
            } else if (cmdsObj != null) {
                patterns.add(normalize(cmdsObj.toString()));
            }

            // groups
            Object grpsObj = map.get("groups");
            Set<String> groups = new HashSet<>();
            if (grpsObj instanceof List<?> grpList) {
                for (Object g : grpList) groups.add(g.toString().toLowerCase().trim());
            } else if (grpsObj != null) {
                groups.add(grpsObj.toString().toLowerCase().trim());
            }

            // message (optional)
            String msg = map.containsKey("message") && map.get("message") != null
                    ? map.get("message").toString()
                    : defaultMessage;

            if (!patterns.isEmpty() && !groups.isEmpty()) {
                rules.add(new BlockRule(patterns, groups, msg));
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Elimina el prefijo "/" y pasa a minúsculas. No toca el wildcard "*". */
    private static String normalize(String s) {
        return s.trim().toLowerCase().replaceFirst("^/+", "").split("\\s+")[0];
    }

    private static boolean matchesAnyPattern(String cmd, List<String> patterns) {
        for (String p : patterns) {
            if (p.endsWith("*")) {
                if (cmd.startsWith(p.substring(0, p.length() - 1))) return true;
            } else {
                if (cmd.equals(p)) return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyGroup(Set<String> playerGroups, Set<String> ruleGroups) {
        for (String g : ruleGroups) {
            if (playerGroups.contains(g)) return true;
        }
        return false;
    }

    /** Obtiene todos los grupos LP heredados del jugador (contextuales). */
    private Set<String> getPlayerGroups(Player player) {
        Set<String> groups = new HashSet<>();
        if (luckPerms == null) return groups;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return groups;
        user.getInheritedGroups(QueryOptions.nonContextual())
                .forEach(g -> groups.add(g.getName().toLowerCase()));
        return groups;
    }

    private void tryLoadLP() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
            }
        } catch (IllegalStateException ignored) {}
        if (luckPerms == null) {
            plugin.getLogger().warning(
                    "[CmdBlock] LuckPerms no encontrado – " +
                    "las reglas de blocked-commands.yml no tendrán efecto.");
        }
    }

    private void ensureDefault() {
        if (!configFile.exists()) {
            plugin.saveResource("blocked-commands.yml", false);
        }
    }
}
