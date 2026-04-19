package com.etcmc.etccore.listener;

import com.etcmc.etccore.ETCCore;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Cancela el daño PvP entre jugadores cuando alguno tiene el toggle PvP desactivado.
 * El estado se guarda como variable "pvp" en PlayerDataManager (default: true).
 */
public class PVPListener implements Listener {

    public static final String VAR = "pvp";
    public static final boolean DEFAULT = true;

    private final ETCCore plugin;

    public PVPListener(ETCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player victim = e.getEntity() instanceof Player p ? p : null;
        if (victim == null) return;

        Player attacker = resolveAttacker(e);
        if (attacker == null || attacker.equals(victim)) return;

        boolean victimOn   = plugin.getPlayerDataManager().getBool(victim.getUniqueId(),   VAR, DEFAULT);
        boolean attackerOn = plugin.getPlayerDataManager().getBool(attacker.getUniqueId(), VAR, DEFAULT);

        if (!victimOn || !attackerOn) {
            e.setCancelled(true);
            // Mensaje al atacante (anti-spam: solo cada 2s)
            long now = System.currentTimeMillis();
            Long last = lastNotice.get(attacker.getUniqueId());
            if (last == null || now - last > 2000L) {
                lastNotice.put(attacker.getUniqueId(), now);
                String who = !victimOn ? victim.getName() + " tiene" : "Tú tienes";
                attacker.sendMessage("§c" + who + " el PvP desactivado.");
            }
        }
    }

    private final java.util.Map<java.util.UUID, Long> lastNotice = new java.util.concurrent.ConcurrentHashMap<>();

    private Player resolveAttacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }
}
