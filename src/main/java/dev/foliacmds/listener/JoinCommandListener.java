package dev.foliacmds.listener;

import dev.foliacmds.FoliaCustomCommands;
import dev.foliacmds.command.CustomCommand;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ejecuta acciones configurables al entrar un jugador.
 *
 * Soporta múltiples reglas bajo join-rules.<nombre> y mantiene compatibilidad
 * con la antigua sección on-join.
 */
public class JoinCommandListener implements Listener {

    private static final String LEGACY_RULE_NAME = "legacy-on-join";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final FoliaCustomCommands plugin;
    private LuckPerms luckPerms;

    public JoinCommandListener(FoliaCustomCommands plugin) {
        this.plugin = plugin;
        tryLoadLP();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<JoinRule> rules = loadRules();
        for (JoinRule rule : rules) {
            processRule(player, rule);
        }
    }

    private List<JoinRule> loadRules() {
        ConfigurationSection rulesSection = plugin.getFeatureConfigManager()
                .getOnJoinConfig()
                .getConfigurationSection("join-rules");
        if (rulesSection != null && !rulesSection.getKeys(false).isEmpty()) {
            List<JoinRule> rules = new ArrayList<>();
            for (String key : rulesSection.getKeys(false)) {
                ConfigurationSection section = rulesSection.getConfigurationSection(key);
                if (section != null) {
                    rules.add(JoinRule.fromConfig(key, section));
                }
            }
            return rules;
        }

        ConfigurationSection legacySection = plugin.getFeatureConfigManager()
            .getOnJoinConfig()
            .getConfigurationSection("on-join");
        if (legacySection == null) {
            return Collections.emptyList();
        }
        return List.of(JoinRule.fromConfig(LEGACY_RULE_NAME, legacySection));
    }

    private void processRule(Player player, JoinRule rule) {
        if (!rule.enabled) {
            return;
        }
        if (!matchesFilters(player, rule)) {
            return;
        }

        boolean firstJoin = !player.hasPlayedBefore();
        List<String> actionsToRun = new ArrayList<>();
        actionsToRun.addAll(rule.alwaysActions);
        if (firstJoin) {
            actionsToRun.addAll(rule.firstJoinActions);
        }
        actionsToRun.removeIf(action -> action == null || action.isBlank());
        if (actionsToRun.isEmpty()) {
            return;
        }

        if (rule.oncePerDay && alreadyRanToday(player, rule)) {
            return;
        }
        if (rule.cooldownSeconds > 0 && isOnCooldown(player, rule)) {
            return;
        }

        executeActions(player, rule, actionsToRun);
        markExecuted(player, rule);
    }

    private void executeActions(Player player, JoinRule rule, List<String> actions) {
        Runnable runner = () -> runActions(player, actions);
        if (rule.delayTicks > 0) {
            player.getScheduler().runDelayed(plugin, task -> runner.run(), null, rule.delayTicks);
            return;
        }
        runner.run();
    }

    private boolean matchesFilters(Player player, JoinRule rule) {
        if (!rule.onlyGroups.isEmpty() && !matchesGroupFilter(player, rule.onlyGroups)) {
            return false;
        }
        if (!rule.excludedGroups.isEmpty() && matchesGroupFilter(player, rule.excludedGroups)) {
            return false;
        }
        if (!rule.onlyPermissions.isEmpty() && !matchesAnyPermission(player, rule.onlyPermissions)) {
            return false;
        }
        if (!rule.excludedPermissions.isEmpty() && matchesAnyPermission(player, rule.excludedPermissions)) {
            return false;
        }
        if (!rule.onlyWorlds.isEmpty() && !rule.onlyWorlds.contains(player.getWorld().getName().toLowerCase())) {
            return false;
        }
        if (!rule.excludedWorlds.isEmpty() && rule.excludedWorlds.contains(player.getWorld().getName().toLowerCase())) {
            return false;
        }
        return true;
    }

    private void runActions(Player player, List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        for (String action : actions) {
            if (action == null || action.isBlank()) {
                continue;
            }
            CustomCommand.fireAction(plugin, player, action, new String[0]);
        }
    }

    private boolean matchesAnyPermission(Player player, List<String> permissions) {
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGroupFilter(Player player, List<String> onlyGroups) {
        if (onlyGroups == null || onlyGroups.isEmpty()) {
            return true;
        }
        if (luckPerms == null) {
            return false;
        }

        Set<String> playerGroups = getPlayerGroups(player);
        if (playerGroups.isEmpty()) {
            return false;
        }

        for (String group : onlyGroups) {
            if (group != null && playerGroups.contains(group.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean alreadyRanToday(Player player, JoinRule rule) {
        String key = stateKey(rule.name, "last-date");
        String today = LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMAT);
        return today.equals(plugin.getPlayerDataManager().get(player.getUniqueId(), key));
    }

    private boolean isOnCooldown(Player player, JoinRule rule) {
        String value = plugin.getPlayerDataManager().get(player.getUniqueId(), stateKey(rule.name, "last-run"));
        if (value.isBlank()) {
            return false;
        }
        try {
            long lastRun = Long.parseLong(value);
            long nextAllowed = lastRun + (rule.cooldownSeconds * 1000L);
            return System.currentTimeMillis() < nextAllowed;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void markExecuted(Player player, JoinRule rule) {
        String now = String.valueOf(System.currentTimeMillis());
        plugin.getPlayerDataManager().set(player.getUniqueId(), stateKey(rule.name, "last-run"), now);
        if (rule.oncePerDay) {
            String today = LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMAT);
            plugin.getPlayerDataManager().set(player.getUniqueId(), stateKey(rule.name, "last-date"), today);
        }
    }

    private String stateKey(String ruleName, String suffix) {
        return "joinrule." + sanitizeRuleName(ruleName) + "." + suffix;
    }

    private String sanitizeRuleName(String ruleName) {
        return ruleName.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }

    private Set<String> getPlayerGroups(Player player) {
        Set<String> groups = new HashSet<>();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return groups;
        }

        groups.add(user.getPrimaryGroup().toLowerCase());
        user.getInheritedGroups(QueryOptions.nonContextual())
                .forEach(group -> groups.add(group.getName().toLowerCase()));
        return groups;
    }

    private void tryLoadLP() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
            }
        } catch (IllegalStateException ignored) {}
    }

    private static List<String> normalizeList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.toLowerCase());
            }
        }
        return normalized;
    }

    private static final class JoinRule {
        private final String name;
        private final boolean enabled;
        private final List<String> onlyGroups;
        private final List<String> excludedGroups;
        private final List<String> onlyPermissions;
        private final List<String> excludedPermissions;
        private final List<String> onlyWorlds;
        private final List<String> excludedWorlds;
        private final long cooldownSeconds;
        private final boolean oncePerDay;
        private final long delayTicks;
        private final List<String> alwaysActions;
        private final List<String> firstJoinActions;

        private JoinRule(String name,
                         boolean enabled,
                         List<String> onlyGroups,
                         List<String> excludedGroups,
                         List<String> onlyPermissions,
                         List<String> excludedPermissions,
                         List<String> onlyWorlds,
                         List<String> excludedWorlds,
                         long cooldownSeconds,
                         boolean oncePerDay,
                         long delayTicks,
                         List<String> alwaysActions,
                         List<String> firstJoinActions) {
            this.name = name;
            this.enabled = enabled;
            this.onlyGroups = onlyGroups;
            this.excludedGroups = excludedGroups;
            this.onlyPermissions = onlyPermissions;
            this.excludedPermissions = excludedPermissions;
            this.onlyWorlds = onlyWorlds;
            this.excludedWorlds = excludedWorlds;
            this.cooldownSeconds = cooldownSeconds;
            this.oncePerDay = oncePerDay;
            this.delayTicks = delayTicks;
            this.alwaysActions = alwaysActions;
            this.firstJoinActions = firstJoinActions;
        }

        private static JoinRule fromConfig(String name, ConfigurationSection section) {
            return new JoinRule(
                    name,
                    section.getBoolean("enabled", false),
                    normalizeList(section.getStringList("only-groups")),
                    normalizeList(section.getStringList("excluded-groups")),
                    section.getStringList("only-permissions"),
                    section.getStringList("excluded-permissions"),
                    normalizeList(section.getStringList("only-worlds")),
                    normalizeList(section.getStringList("excluded-worlds")),
                    Math.max(0L, section.getLong("cooldown-seconds", 0L)),
                    section.getBoolean("once-per-day", false),
                    Math.max(0L, section.getLong("delay-ticks", 0L)),
                    new ArrayList<>(section.getStringList("always-actions")),
                    new ArrayList<>(section.getStringList("first-join-actions"))
            );
        }
    }
}