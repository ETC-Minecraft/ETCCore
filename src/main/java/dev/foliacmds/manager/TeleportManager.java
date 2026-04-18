package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TeleportManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final FoliaCustomCommands plugin;
    private final File dataDir;
    private final File playersDir;
    private final File globalFile;
    private final Object ioLock = new Object();

    private final Map<UUID, List<TeleportRequest>> requestsByTarget = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, Map<TeleportType, Long>> cooldowns = new HashMap<>();
    private LuckPerms luckPerms;

    public TeleportManager(FoliaCustomCommands plugin) {
        this.plugin = plugin;
        dataDir = new File(plugin.getDataFolder(), "teleports");
        playersDir = new File(dataDir, "players");
        globalFile = new File(dataDir, "global.yml");
        if (!playersDir.exists()) {
            playersDir.mkdirs();
        }
        tryLoadLuckPerms();
    }

    public void reload() {
        cleanupExpiredRequests();
    }

    public String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,24}");
    }

    public int getHomeLimit(Player player, boolean publicHome) {
        String basePath = publicHome ? "homes.public-default-limit" : "homes.default-limit";
        String prefix = publicHome ? "etccore.publichome.limit." : "etccore.home.limit.";
        int limit = plugin.getFeatureConfigManager().getTeleportConfig().getInt(basePath, publicHome ? 1 : 3);
        String typeKey = publicHome ? "public" : "private";

        ConfigurationSection groupLimits = plugin.getFeatureConfigManager().getTeleportConfig().getConfigurationSection("homes.group-limits");
        if (groupLimits != null) {
            for (String group : getPlayerGroups(player)) {
                int groupLimit = groupLimits.getInt(group + "." + typeKey, -1);
                if (groupLimit >= 0) {
                    limit = Math.max(limit, groupLimit);
                }
            }
        }

        for (var permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) {
                continue;
            }
            String permission = permissionInfo.getPermission().toLowerCase(Locale.ROOT);
            if (permission.startsWith(prefix)) {
                String suffix = permission.substring(prefix.length());
                try {
                    limit = Math.max(limit, Integer.parseInt(suffix));
                } catch (NumberFormatException ignored) {}
            }
        }
        return Math.max(0, limit);
    }

    public boolean executeTeleport(Player player,
                                   Location location,
                                   String successMessage,
                                   TeleportType type,
                                   boolean bypassDelaysAndCooldowns) {
        if (!bypassDelaysAndCooldowns) {
            long remainingMs = getRemainingCooldownMs(player.getUniqueId(), type);
            if (remainingMs > 0L) {
                player.sendMessage("§cDebes esperar §f" + formatDurationSeconds(remainingMs) + "§c antes de volver a usar este teletransporte.");
                return false;
            }
        }

        long warmupSeconds = bypassDelaysAndCooldowns ? 0L : getWarmupSeconds(player, type);
        if (warmupSeconds <= 0L) {
            performTeleport(player, location, successMessage, type, !bypassDelaysAndCooldowns);
            return true;
        }

        long token = System.nanoTime();
        PendingTeleport pending = new PendingTeleport(player.getUniqueId(), location.clone(), player.getLocation().clone(), successMessage, type, token);
        synchronized (pendingTeleports) {
            pendingTeleports.put(player.getUniqueId(), pending);
        }
        player.sendMessage("§eNo te muevas. Teleport en §f" + warmupSeconds + "s§e.");
        player.getScheduler().runDelayed(plugin, task -> completePendingTeleport(player, token), null, warmupSeconds * 20L);
        return true;
    }

    public void cancelPendingTeleport(Player player, String message) {
        PendingTeleport removed;
        synchronized (pendingTeleports) {
            removed = pendingTeleports.remove(player.getUniqueId());
        }
        if (removed != null && message != null && !message.isBlank()) {
            player.sendMessage(message);
        }
    }

    public PendingTeleport getPendingTeleport(UUID uuid) {
        synchronized (pendingTeleports) {
            return pendingTeleports.get(uuid);
        }
    }

    public boolean shouldCancelOnMove(TeleportType type) {
        return plugin.getFeatureConfigManager().getTeleportConfig().getBoolean("warmups.cancel-on-move." + type.configKey(), true);
    }

    public double getCancelMoveDistance() {
        return Math.max(0.05D, plugin.getFeatureConfigManager().getTeleportConfig().getDouble("warmups.cancel-move-distance", 0.15D));
    }

    public boolean setHome(Player player, String name, Location location, boolean publicHome, boolean requireExisting) {
        String key = normalizeName(name);
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(player.getUniqueId());
            touchPlayer(cfg, player);

            boolean exists = cfg.isConfigurationSection("homes." + key);
            if (requireExisting && !exists) {
                return false;
            }
            if (!exists) {
                int currentCount = countHomes(cfg, publicHome);
                if (currentCount >= getHomeLimit(player, publicHome)) {
                    return false;
                }
            }

            writeLocation(cfg, "homes." + key + ".location", location);
            cfg.set("homes." + key + ".public", publicHome);
            cfg.set("homes." + key + ".updated-at", System.currentTimeMillis());
            savePlayerConfig(player.getUniqueId(), cfg);
            syncPublicHome(player.getUniqueId(), player.getName(), key, publicHome, location);
            return true;
        }
    }

    public boolean setHomeVisibility(Player player, String name, boolean publicHome) {
        String key = normalizeName(name);
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(player.getUniqueId());
            touchPlayer(cfg, player);
            ConfigurationSection section = cfg.getConfigurationSection("homes." + key);
            if (section == null) {
                return false;
            }
            boolean current = section.getBoolean("public", false);
            if (current == publicHome) {
                return true;
            }
            if (publicHome) {
                int currentCount = countHomes(cfg, true);
                if (currentCount >= getHomeLimit(player, true)) {
                    return false;
                }
            }
            section.set("public", publicHome);
            savePlayerConfig(player.getUniqueId(), cfg);
            Location location = readLocation(section.getConfigurationSection("location"));
            syncPublicHome(player.getUniqueId(), player.getName(), key, publicHome, location);
            return true;
        }
    }

    public Boolean toggleHomeVisibility(Player player, String name) {
        String key = normalizeName(name);
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(player.getUniqueId());
            touchPlayer(cfg, player);
            ConfigurationSection section = cfg.getConfigurationSection("homes." + key);
            if (section == null) {
                return null;
            }
            boolean newValue = !section.getBoolean("public", false);
            if (newValue) {
                int currentCount = countHomes(cfg, true);
                if (currentCount >= getHomeLimit(player, true)) {
                    return null;
                }
            }
            section.set("public", newValue);
            savePlayerConfig(player.getUniqueId(), cfg);
            Location location = readLocation(section.getConfigurationSection("location"));
            syncPublicHome(player.getUniqueId(), player.getName(), key, newValue, location);
            return newValue;
        }
    }

    public boolean deleteHome(UUID uuid, String name) {
        String key = normalizeName(name);
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(uuid);
            ConfigurationSection section = cfg.getConfigurationSection("homes." + key);
            if (section == null) {
                return false;
            }
            cfg.set("homes." + key, null);
            savePlayerConfig(uuid, cfg);
            syncPublicHome(uuid, cfg.getString("player.last-name", uuid.toString()), key, false, null);
            return true;
        }
    }

    public Location getHome(UUID uuid, String name) {
        synchronized (ioLock) {
            ConfigurationSection section = loadPlayerConfig(uuid).getConfigurationSection("homes." + normalizeName(name) + ".location");
            return readLocation(section);
        }
    }

    public List<HomeRecord> listHomes(UUID uuid) {
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(uuid);
            ConfigurationSection section = cfg.getConfigurationSection("homes");
            if (section == null) {
                return Collections.emptyList();
            }
            List<HomeRecord> homes = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                ConfigurationSection home = section.getConfigurationSection(key);
                if (home == null) {
                    continue;
                }
                Location location = readLocation(home.getConfigurationSection("location"));
                if (location == null) {
                    continue;
                }
                homes.add(new HomeRecord(key, location, home.getBoolean("public", false)));
            }
            homes.sort(Comparator.comparing(HomeRecord::name));
            return homes;
        }
    }

    public Location getPublicHome(String ownerToken, String homeName) {
        synchronized (ioLock) {
            ConfigurationSection roots = loadGlobalConfig().getConfigurationSection("public-homes");
            if (roots == null) {
                return null;
            }
            for (String uuidKey : roots.getKeys(false)) {
                ConfigurationSection ownerSection = roots.getConfigurationSection(uuidKey);
                if (ownerSection == null) {
                    continue;
                }
                String ownerName = ownerSection.getString("owner-name", "");
                if (!uuidKey.equalsIgnoreCase(ownerToken) && !ownerName.equalsIgnoreCase(ownerToken)) {
                    continue;
                }
                ConfigurationSection home = ownerSection.getConfigurationSection(normalizeName(homeName));
                if (home != null) {
                    return readLocation(home.getConfigurationSection("location"));
                }
            }
            return null;
        }
    }

    public List<String> listPublicHomes(String ownerFilter) {
        synchronized (ioLock) {
            List<String> homes = new ArrayList<>();
            ConfigurationSection roots = loadGlobalConfig().getConfigurationSection("public-homes");
            if (roots == null) {
                return homes;
            }
            for (String uuidKey : roots.getKeys(false)) {
                ConfigurationSection ownerSection = roots.getConfigurationSection(uuidKey);
                if (ownerSection == null) {
                    continue;
                }
                String ownerName = ownerSection.getString("owner-name", uuidKey);
                if (ownerFilter != null && !ownerFilter.isBlank()
                        && !ownerName.equalsIgnoreCase(ownerFilter)
                        && !uuidKey.equalsIgnoreCase(ownerFilter)) {
                    continue;
                }
                for (String key : ownerSection.getKeys(false)) {
                    if ("owner-name".equalsIgnoreCase(key)) {
                        continue;
                    }
                    homes.add(ownerName + ":" + key);
                }
            }
            Collections.sort(homes);
            return homes;
        }
    }

    public boolean setWarp(String name, Location location, String editor, boolean requireExisting) {
        String key = normalizeName(name);
        synchronized (ioLock) {
            YamlConfiguration cfg = loadGlobalConfig();
            boolean exists = cfg.isConfigurationSection("warps." + key);
            if (requireExisting && !exists) {
                return false;
            }
            writeLocation(cfg, "warps." + key + ".location", location);
            cfg.set("warps." + key + ".editor", editor);
            cfg.set("warps." + key + ".updated-at", System.currentTimeMillis());
            saveGlobalConfig(cfg);
            return true;
        }
    }

    public boolean deleteWarp(String name) {
        synchronized (ioLock) {
            YamlConfiguration cfg = loadGlobalConfig();
            String key = normalizeName(name);
            if (!cfg.isConfigurationSection("warps." + key)) {
                return false;
            }
            cfg.set("warps." + key, null);
            saveGlobalConfig(cfg);
            return true;
        }
    }

    public Location getWarp(String name) {
        synchronized (ioLock) {
            ConfigurationSection section = loadGlobalConfig().getConfigurationSection("warps." + normalizeName(name) + ".location");
            return readLocation(section);
        }
    }

    public List<String> listWarps() {
        synchronized (ioLock) {
            ConfigurationSection section = loadGlobalConfig().getConfigurationSection("warps");
            if (section == null) {
                return Collections.emptyList();
            }
            List<String> warps = new ArrayList<>(section.getKeys(false));
            Collections.sort(warps);
            return warps;
        }
    }

    public void setNamedLocation(String key, Location location) {
        synchronized (ioLock) {
            YamlConfiguration cfg = loadGlobalConfig();
            writeLocation(cfg, key, location);
            saveGlobalConfig(cfg);
        }
    }

    public Location getNamedLocation(String key) {
        synchronized (ioLock) {
            return readLocation(loadGlobalConfig().getConfigurationSection(key));
        }
    }

    public void recordBackLocation(UUID uuid, Location location) {
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(uuid);
            writeLocation(cfg, "back", location);
            savePlayerConfig(uuid, cfg);
        }
    }

    public Location getBackLocation(UUID uuid) {
        synchronized (ioLock) {
            return readLocation(loadPlayerConfig(uuid).getConfigurationSection("back"));
        }
    }

    public void recordLogout(Player player) {
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(player.getUniqueId());
            touchPlayer(cfg, player);
            writeLocation(cfg, "logout", player.getLocation());
            savePlayerConfig(player.getUniqueId(), cfg);
        }
    }

    public Location getOfflineLocation(String playerToken) {
        UUID parsedUuid = tryParseUuid(playerToken);
        synchronized (ioLock) {
            if (parsedUuid != null) {
                return readLocation(loadPlayerConfig(parsedUuid).getConfigurationSection("logout"));
            }
            File[] files = playersDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) {
                return null;
            }
            for (File file : files) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                String lastName = cfg.getString("player.last-name", "");
                if (lastName.equalsIgnoreCase(playerToken)) {
                    return readLocation(cfg.getConfigurationSection("logout"));
                }
            }
            return null;
        }
    }

    public void recordDeath(Player player, Location location) {
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(player.getUniqueId());
            touchPlayer(cfg, player);

            List<DeathRecord> deaths = listDeathsInternal(cfg);
            deaths.add(0, new DeathRecord(location, System.currentTimeMillis()));
            int limit = Math.max(1, plugin.getFeatureConfigManager().getTeleportConfig().getInt("deaths.history-limit", 3));
            while (deaths.size() > limit) {
                deaths.remove(deaths.size() - 1);
            }
            writeDeaths(cfg, deaths);
            savePlayerConfig(player.getUniqueId(), cfg);
        }
    }

    public List<DeathRecord> listDeaths(UUID uuid) {
        synchronized (ioLock) {
            return listDeathsInternal(loadPlayerConfig(uuid));
        }
    }

    public DeathRecord getDeath(UUID uuid, int index) {
        List<DeathRecord> deaths = listDeaths(uuid);
        if (index < 1 || index > deaths.size()) {
            return null;
        }
        return deaths.get(index - 1);
    }

    public boolean toggleIgnore(Player player, Player target) {
        synchronized (ioLock) {
            YamlConfiguration cfg = loadPlayerConfig(player.getUniqueId());
            touchPlayer(cfg, player);
            List<String> ignored = new ArrayList<>(cfg.getStringList("ignored"));
            String key = target.getUniqueId().toString();
            boolean nowIgnoring;
            if (ignored.contains(key)) {
                ignored.remove(key);
                nowIgnoring = false;
            } else {
                ignored.add(key);
                nowIgnoring = true;
            }
            cfg.set("ignored", ignored);
            savePlayerConfig(player.getUniqueId(), cfg);
            return nowIgnoring;
        }
    }

    public boolean isIgnoring(UUID owner, UUID target) {
        synchronized (ioLock) {
            return loadPlayerConfig(owner).getStringList("ignored").contains(target.toString());
        }
    }

    public RequestStatus createRequest(Player requester, Player target, RequestType type) {
        cleanupExpiredRequests();
        synchronized (requestsByTarget) {
            if (isIgnoring(target.getUniqueId(), requester.getUniqueId())) {
                return RequestStatus.IGNORED;
            }
            List<TeleportRequest> requests = requestsByTarget.computeIfAbsent(target.getUniqueId(), ignored -> new ArrayList<>());
            requests.removeIf(request -> request.requesterId.equals(requester.getUniqueId()));
            requests.add(new TeleportRequest(requester.getUniqueId(), target.getUniqueId(), type, System.currentTimeMillis()));
            return RequestStatus.CREATED;
        }
    }

    public List<TeleportRequest> getRequests(UUID targetId) {
        cleanupExpiredRequests();
        synchronized (requestsByTarget) {
            return new ArrayList<>(requestsByTarget.getOrDefault(targetId, Collections.emptyList()));
        }
    }

    public TeleportRequest consumeRequest(UUID targetId, UUID requesterId) {
        cleanupExpiredRequests();
        synchronized (requestsByTarget) {
            List<TeleportRequest> requests = requestsByTarget.get(targetId);
            if (requests == null || requests.isEmpty()) {
                return null;
            }
            TeleportRequest selected = null;
            if (requesterId == null) {
                selected = requests.get(requests.size() - 1);
            } else {
                for (TeleportRequest request : requests) {
                    if (request.requesterId.equals(requesterId)) {
                        selected = request;
                        break;
                    }
                }
            }
            if (selected != null) {
                requests.remove(selected);
                if (requests.isEmpty()) {
                    requestsByTarget.remove(targetId);
                }
            }
            return selected;
        }
    }

    public void cleanupExpiredRequests() {
        long maxAge = Math.max(5L, plugin.getFeatureConfigManager().getTeleportConfig().getLong("tpa.expire-seconds", 60L)) * 1000L;
        long now = System.currentTimeMillis();
        synchronized (requestsByTarget) {
            requestsByTarget.values().forEach(list -> list.removeIf(request -> (now - request.createdAt) > maxAge));
            requestsByTarget.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    public long getWarmupSeconds(Player player, TeleportType type) {
        if (player.hasPermission("etccore.teleport.warmup.bypass")
                || player.hasPermission("etccore.teleport.warmup." + type.configKey() + ".bypass")) {
            return 0L;
        }
        return Math.max(0L, plugin.getFeatureConfigManager().getTeleportConfig().getLong("warmups." + type.configKey(), 0L));
    }

    public long getCooldownSeconds(Player player, TeleportType type) {
        if (player.hasPermission("etccore.teleport.cooldown.bypass")
                || player.hasPermission("etccore.teleport.cooldown." + type.configKey() + ".bypass")) {
            return 0L;
        }
        return Math.max(0L, plugin.getFeatureConfigManager().getTeleportConfig().getLong("cooldowns." + type.configKey(), 0L));
    }

    public Location findRandomTeleportLocation(World world) {
        int minRadius = Math.max(0, plugin.getFeatureConfigManager().getTeleportConfig().getInt("rtp.min-radius", 250));
        int maxRadius = Math.max(minRadius + 1, plugin.getFeatureConfigManager().getTeleportConfig().getInt("rtp.max-radius", 2500));
        int attempts = Math.max(1, plugin.getFeatureConfigManager().getTeleportConfig().getInt("rtp.attempts", 16));
        Location center = world.getSpawnLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            int distance = random.nextInt(minRadius, maxRadius + 1);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location location = new Location(world, x + 0.5D, y, z + 0.5D);
            if (isSafeLocation(location)) {
                return location;
            }
        }
        return null;
    }

    public String formatLocationBrief(Location location) {
        return location.getWorld().getName() + " "
                + location.getBlockX() + ", "
                + location.getBlockY() + ", "
                + location.getBlockZ();
    }

    public String formatTimestamp(long timestamp) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    public String formatDurationSeconds(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        return seconds + "s";
    }

    private int countHomes(YamlConfiguration cfg, boolean publicHome) {
        ConfigurationSection section = cfg.getConfigurationSection("homes");
        if (section == null) {
            return 0;
        }
        int count = 0;
        for (String key : section.getKeys(false)) {
            if (section.getBoolean(key + ".public", false) == publicHome) {
                count++;
            }
        }
        return count;
    }

    private void completePendingTeleport(Player player, long token) {
        PendingTeleport pending;
        synchronized (pendingTeleports) {
            pending = pendingTeleports.get(player.getUniqueId());
            if (pending == null || pending.token() != token) {
                return;
            }
            pendingTeleports.remove(player.getUniqueId());
        }
        performTeleport(player, pending.destination(), pending.successMessage(), pending.type(), true);
    }

    private void performTeleport(Player player,
                                 Location location,
                                 String successMessage,
                                 TeleportType type,
                                 boolean applyCooldown) {
        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                if (applyCooldown) {
                    long cooldownSeconds = getCooldownSeconds(player, type);
                    if (cooldownSeconds > 0L) {
                        synchronized (cooldowns) {
                            cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                                    .put(type, System.currentTimeMillis() + (cooldownSeconds * 1000L));
                        }
                    }
                }
                player.sendMessage(successMessage);
            } else {
                player.sendMessage("§cNo se pudo completar el teletransporte.");
            }
        });
    }

    private long getRemainingCooldownMs(UUID uuid, TeleportType type) {
        synchronized (cooldowns) {
            Map<TeleportType, Long> playerCooldowns = cooldowns.get(uuid);
            if (playerCooldowns == null) {
                return 0L;
            }
            long expiresAt = playerCooldowns.getOrDefault(type, 0L);
            long remaining = expiresAt - System.currentTimeMillis();
            if (remaining <= 0L) {
                playerCooldowns.remove(type);
                if (playerCooldowns.isEmpty()) {
                    cooldowns.remove(uuid);
                }
                return 0L;
            }
            return remaining;
        }
    }

    private List<String> getPlayerGroups(Player player) {
        if (luckPerms == null) {
            return Collections.emptyList();
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return Collections.emptyList();
        }
        List<String> groups = new ArrayList<>();
        groups.add(user.getPrimaryGroup().toLowerCase(Locale.ROOT));
        user.getInheritedGroups(QueryOptions.nonContextual())
                .forEach(group -> groups.add(group.getName().toLowerCase(Locale.ROOT)));
        return groups;
    }

    private void tryLoadLuckPerms() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
            }
        } catch (IllegalStateException ignored) {}
    }

    private void syncPublicHome(UUID ownerUuid, String ownerName, String homeName, boolean publicHome, Location location) {
        YamlConfiguration cfg = loadGlobalConfig();
        String basePath = "public-homes." + ownerUuid;
        cfg.set(basePath + ".owner-name", ownerName);
        if (publicHome && location != null) {
            writeLocation(cfg, basePath + "." + homeName + ".location", location);
            cfg.set(basePath + "." + homeName + ".updated-at", System.currentTimeMillis());
        } else {
            cfg.set(basePath + "." + homeName, null);
        }
        saveGlobalConfig(cfg);
    }

    private List<DeathRecord> listDeathsInternal(YamlConfiguration cfg) {
        ConfigurationSection section = cfg.getConfigurationSection("deaths");
        if (section == null) {
            return new ArrayList<>();
        }
        List<DeathRecord> deaths = new ArrayList<>();
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(Comparator.comparingInt(Integer::parseInt));
        for (String key : keys) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Location location = readLocation(entry.getConfigurationSection("location"));
            if (location != null) {
                deaths.add(new DeathRecord(location, entry.getLong("at", 0L)));
            }
        }
        return deaths;
    }

    private void writeDeaths(YamlConfiguration cfg, List<DeathRecord> deaths) {
        cfg.set("deaths", null);
        for (int i = 0; i < deaths.size(); i++) {
            DeathRecord record = deaths.get(i);
            String path = "deaths." + i;
            writeLocation(cfg, path + ".location", record.location());
            cfg.set(path + ".at", record.timestamp());
        }
    }

    private void touchPlayer(YamlConfiguration cfg, Player player) {
        cfg.set("player.last-name", player.getName());
    }

    private boolean isSafeLocation(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()
                && ground.getType() != Material.LAVA && ground.getType() != Material.WATER;
    }

    private YamlConfiguration loadPlayerConfig(UUID uuid) {
        return YamlConfiguration.loadConfiguration(playerFile(uuid));
    }

    private void savePlayerConfig(UUID uuid, YamlConfiguration cfg) {
        try {
            cfg.save(playerFile(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar teleports del jugador " + uuid + ": " + e.getMessage());
        }
    }

    private File playerFile(UUID uuid) {
        return new File(playersDir, uuid + ".yml");
    }

    private YamlConfiguration loadGlobalConfig() {
        return YamlConfiguration.loadConfiguration(globalFile);
    }

    private void saveGlobalConfig(YamlConfiguration cfg) {
        try {
            cfg.save(globalFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar teleports globales: " + e.getMessage());
        }
    }

    private void writeLocation(YamlConfiguration cfg, String path, Location location) {
        cfg.set(path, null);
        cfg.set(path + ".world", location.getWorld().getName());
        cfg.set(path + ".x", location.getX());
        cfg.set(path + ".y", location.getY());
        cfg.set(path + ".z", location.getZ());
        cfg.set(path + ".yaw", location.getYaw());
        cfg.set(path + ".pitch", location.getPitch());
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world", "");
        if (worldName.isBlank()) {
            return null;
        }
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0D),
                (float) section.getDouble("pitch", 0.0D)
        );
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record HomeRecord(String name, Location location, boolean isPublic) {}

    public record DeathRecord(Location location, long timestamp) {}

    public record PendingTeleport(UUID playerId,
                                  Location destination,
                                  Location origin,
                                  String successMessage,
                                  TeleportType type,
                                  long token) {}

    public enum TeleportType {
        HOME("home"),
        PUBLIC_HOME("publichome"),
        WARP("warp"),
        LOBBY("lobby"),
        SPAWN("spawn"),
        BACK("back"),
        REBORN("reborn"),
        RTP("rtp"),
        TPA("tpa"),
        TP_ADMIN("tp-admin");

        private final String configKey;

        TeleportType(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }
    }

    public enum RequestType {
        TO_TARGET,
        TARGET_HERE
    }

    public enum RequestStatus {
        CREATED,
        IGNORED
    }

    public static final class TeleportRequest {
        private final UUID requesterId;
        private final UUID targetId;
        private final RequestType type;
        private final long createdAt;

        public TeleportRequest(UUID requesterId, UUID targetId, RequestType type, long createdAt) {
            this.requesterId = requesterId;
            this.targetId = targetId;
            this.type = type;
            this.createdAt = createdAt;
        }

        public UUID requesterId() {
            return requesterId;
        }

        public UUID targetId() {
            return targetId;
        }

        public RequestType type() {
            return type;
        }

        public long createdAt() {
            return createdAt;
        }
    }
}