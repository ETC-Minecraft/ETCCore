package com.etcmc.etccore.manager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Soft-dependency wrapper for Vault Economy.
 *
 * Usage in YAML commands / menus:
 *   [MONEY_GIVE:100]         — give 100 to the player
 *   [MONEY_TAKE:50]          — take 50 (fails silently if insufficient; use [IF money>=50])
 *   [IF money>=1000]         — condition: player has at least 1000
 *   {balance}                — current balance (raw number)
 *   {balance_fmt}            — formatted by the economy plugin (e.g. "$1,000.00")
 *
 * Folia note: Vault's Economy API makes sync calls. Always invoke these methods
 * within a player.getScheduler().run() context (i.e., inside an action that is
 * already running on the player's region thread). ETCCore's action engine already
 * satisfies this requirement.
 */
public class VaultManager {

    private final JavaPlugin plugin;
    private Economy          economy;
    private boolean          enabled;

    public VaultManager(JavaPlugin plugin) {
        this.plugin  = plugin;
        this.enabled = tryLoad();
    }

    private boolean tryLoad() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning(
                    "[VaultManager] Vault detectado pero no hay plugin de economía instalado.");
            return false;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info(
                "[VaultManager] Economía vinculada: " + economy.getName());
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Give {@code amount} to the player.
     * @return true on success
     */
    public boolean give(Player player, double amount) {
        if (!enabled) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Take {@code amount} from the player.
     * Returns false if the player has insufficient funds (no funds are removed in that case).
     */
    public boolean take(Player player, double amount) {
        if (!enabled) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Check whether the player has at least {@code amount}.
     */
    public boolean has(Player player, double amount) {
        if (!enabled) return false;
        return economy.has(player, amount);
    }

    /**
     * Get the current balance of the player.
     */
    public double getBalance(Player player) {
        if (!enabled) return 0.0;
        return economy.getBalance(player);
    }

    /**
     * Format a balance value using the economy plugin's formatter.
     */
    public String format(double amount) {
        if (!enabled) return String.valueOf((long) amount);
        return economy.format(amount);
    }
}
