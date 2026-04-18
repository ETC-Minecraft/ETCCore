package dev.foliacmds.listener;

import dev.foliacmds.FoliaCustomCommands;
import dev.foliacmds.command.CustomCommand;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mantiene el respawn vanilla por cama/anchor y aplica reglas fallback
 * cuando el jugador no tiene un punto de respawn válido.
 */
public class RespawnListener implements Listener {

    private static final String LEGACY_RULE_NAME = "legacy-death-respawn";

    private final FoliaCustomCommands plugin;
    private LuckPerms luckPerms;

    public RespawnListener(FoliaCustomCommands plugin) {
        this.plugin = plugin;
        tryLoadLP();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }

        Player player = event.getPlayer();
        for (RespawnRule rule : loadRules()) {
            if (applyRule(event, player, rule)) {
                return;
            }
        }
    }

    private List<RespawnRule> loadRules() {
        ConfigurationSection rulesSection = plugin.getFeatureConfigManager()
                .getDeathRespawnConfig()
                .getConfigurationSection("respawn-rules");
        if (rulesSection != null && !rulesSection.getKeys(false).isEmpty()) {
            List<RespawnRule> rules = new ArrayList<>();
            for (String key : rulesSection.getKeys(false)) {
                ConfigurationSection section = rulesSection.getConfigurationSection(key);
                if (section != null) {
                    rules.add(RespawnRule.fromConfig(section));
                }
            }
            return rules;
        }

        ConfigurationSection legacySection = plugin.getFeatureConfigManager()
                .getDeathRespawnConfig()
                .getConfigurationSection("death-respawn");
        if (legacySection == null) {
            return Collections.emptyList();
        }
        return List.of(RespawnRule.fromConfig(legacySection));
    }

    private boolean applyRule(PlayerRespawnEvent event, Player player, RespawnRule rule) {
        if (!rule.enabled) {
            return false;
        }
        if (!matchesFilters(player, rule)) {
            return false;
        }

        Location location = resolveLocation(rule.locationSpec);
        List<String> actions = new ArrayList<>(rule.fallbackActions);
        actions.removeIf(action -> action == null || action.isBlank());
        if (location == null && actions.isEmpty()) {
            return false;
        }

        if (location != null) {
            event.setRespawnLocation(location);
        }
        if (!actions.isEmpty()) {
            player.getScheduler().runDelayed(plugin, task -> {
                for (String action : actions) {
                    CustomCommand.fireAction(plugin, player, action, new String[0]);
                }
            }, null, rule.delayTicks);
        }
        return true;
    }

    private boolean matchesFilters(Player player, RespawnRule rule) {
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

    private boolean matchesAnyPermission(Player player, List<String> permissions) {
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGroupFilter(Player player, List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return true;
        }
        if (luckPerms == null) {
            return false;
        }

        Set<String> playerGroups = getPlayerGroups(player);
        if (playerGroups.isEmpty()) {
            return false;
        }

        for (String group : groups) {
            if (group != null && playerGroups.contains(group.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Location resolveLocation(LocationSpec spec) {
        if (spec == null || spec.worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(spec.worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, spec.x, spec.y, spec.z, spec.yaw, spec.pitch);
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

    private static final class RespawnRule {
        private final boolean enabled;
        private final List<String> onlyGroups;
        private final List<String> excludedGroups;
        private final List<String> onlyPermissions;
        private final List<String> excludedPermissions;
        private final List<String> onlyWorlds;
        private final List<String> excludedWorlds;
        private final long delayTicks;
        private final List<String> fallbackActions;
        private final LocationSpec locationSpec;

        private RespawnRule(boolean enabled,
                            List<String> onlyGroups,
                            List<String> excludedGroups,
                            List<String> onlyPermissions,
                            List<String> excludedPermissions,
                            List<String> onlyWorlds,
                            List<String> excludedWorlds,
                            long delayTicks,
                            List<String> fallbackActions,
                            LocationSpec locationSpec) {
            this.enabled = enabled;
            this.onlyGroups = onlyGroups;
            this.excludedGroups = excludedGroups;
            this.onlyPermissions = onlyPermissions;
            this.excludedPermissions = excludedPermissions;
            this.onlyWorlds = onlyWorlds;
            this.excludedWorlds = excludedWorlds;
            this.delayTicks = delayTicks;
            this.fallbackActions = fallbackActions;
            this.locationSpec = locationSpec;
        }

        private static RespawnRule fromConfig(ConfigurationSection section) {
            return new RespawnRule(
                    section.getBoolean("enabled", false),
                    normalizeList(section.getStringList("only-groups")),
                    normalizeList(section.getStringList("excluded-groups")),
                    section.getStringList("only-permissions"),
                    section.getStringList("excluded-permissions"),
                    normalizeList(section.getStringList("only-worlds")),
                    normalizeList(section.getStringList("excluded-worlds")),
                    Math.max(0L, section.getLong("delay-ticks", 1L)),
                    new ArrayList<>(section.getStringList("fallback-actions")),
                    LocationSpec.fromConfig(section.getConfigurationSection("respawn-location"))
            );
        }
    }

    private static final class LocationSpec {
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private LocationSpec(String worldName, double x, double y, double z, float yaw, float pitch) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private static LocationSpec fromConfig(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            String worldName = section.getString("world", "");
            if (worldName.isBlank()) {
                return null;
            }
            return new LocationSpec(
                    worldName,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw", 0.0D),
                    (float) section.getDouble("pitch", 0.0D)
            );
        }
    }
}