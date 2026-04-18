package com.etcmc.etccore.manager;

import com.etcmc.etccore.ETCCore;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class PlaytimeManager {

    private final ETCCore plugin;
    private ScheduledTask syncTask;

    public PlaytimeManager(ETCCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload();
    }

    public void reload() {
        shutdown();
        if (!isEnabled()) {
            return;
        }
        long intervalSeconds = Math.max(10L, plugin.getFeatureConfigManager().getPlaytimeConfig().getLong("sync-interval-seconds", 60L));
        long ticks = intervalSeconds * 20L;
        syncTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> syncOnlinePlayers(), ticks, ticks);
        syncOnlinePlayers();
    }

    public void shutdown() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        syncOnlinePlayers();
    }

    public boolean isEnabled() {
        return plugin.getFeatureConfigManager().getPlaytimeConfig().getBoolean("enabled", true);
    }

    public void syncOnlinePlayers() {
        if (!isEnabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            syncPlayer(player);
        }
    }

    public void syncPlayer(Player player) {
        if (!isEnabled()) {
            return;
        }
        long ticks = getPlaytimeTicks(player);
        long seconds = ticks / 20L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        String prefix = plugin.getFeatureConfigManager().getPlaytimeConfig().getString("vars-prefix", "playtime");

        plugin.getPlayerDataManager().set(player.getUniqueId(), prefix + ".ticks", String.valueOf(ticks));
        plugin.getPlayerDataManager().set(player.getUniqueId(), prefix + ".seconds", String.valueOf(seconds));
        plugin.getPlayerDataManager().set(player.getUniqueId(), prefix + ".minutes", String.valueOf(minutes));
        plugin.getPlayerDataManager().set(player.getUniqueId(), prefix + ".hours", String.valueOf(hours));
        plugin.getPlayerDataManager().set(player.getUniqueId(), prefix + ".days", String.valueOf(days));
        plugin.getPlayerDataManager().set(player.getUniqueId(), prefix + ".human", formatHuman(seconds));
    }

    public long getPlaytimeTicks(Player player) {
        return Math.max(0L, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    public long getPlaytimeSeconds(Player player) {
        return getPlaytimeTicks(player) / 20L;
    }

    public String getPlaytimeHuman(Player player) {
        return formatHuman(getPlaytimeSeconds(player));
    }

    public String formatHuman(long totalSeconds) {
        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24L;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(seconds).append("s");
        return builder.toString().trim();
    }
}