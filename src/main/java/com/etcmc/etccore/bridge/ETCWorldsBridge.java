package com.etcmc.etccore.bridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a ETCWorlds (PocketWorlds + WorldsManager) por reflexión, para
 * mantener ETCCore desacoplado en compilación.
 *
 * Si ETCWorlds no está cargado todos los métodos devuelven listas vacías
 * o false sin lanzar.
 */
public final class ETCWorldsBridge {

    private final Plugin worldsPlugin;
    private final Object worldsManager;
    private final Object pocketManager;

    // Cache de métodos
    private final Method mGetManagedNames;
    private final Method mGetRules;
    private final Method mPwAll;
    private final Method mPwGet;
    private final Method mPwIsInvited;
    private final Method mPwIsUser;
    private final Field  fPwWorldName;
    private final Field  fPwOwner;

    public ETCWorldsBridge() {
        this.worldsPlugin = Bukkit.getPluginManager().getPlugin("ETCWorlds");
        Object wm = null, pm = null;
        Method gn = null, gr = null, pa = null, pg = null, pi = null, pu = null;
        Field  pwn = null, pwo = null;

        if (worldsPlugin != null) {
            try {
                wm = worldsPlugin.getClass().getMethod("worlds").invoke(worldsPlugin);
                pm = worldsPlugin.getClass().getMethod("pocketWorlds").invoke(worldsPlugin);

                gn = wm.getClass().getMethod("getManagedNames");
                gr = wm.getClass().getMethod("getRules", String.class);

                pa = pm.getClass().getMethod("all");
                pg = pm.getClass().getMethod("get", UUID.class);
                pi = pm.getClass().getMethod("isInvited", UUID.class, UUID.class);
                pu = pm.getClass().getMethod("isUser",    UUID.class, UUID.class);

                Class<?> pwClass = Class.forName("com.etcmc.etcworlds.manager.PocketWorldManager$PocketWorld");
                pwn = pwClass.getField("worldName");
                pwo = pwClass.getField("owner");
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[ETCCore] ETCWorlds detected but bridge init failed: " + t);
                wm = null; pm = null;
            }
        }
        this.worldsManager   = wm;
        this.pocketManager   = pm;
        this.mGetManagedNames= gn;
        this.mGetRules       = gr;
        this.mPwAll          = pa;
        this.mPwGet          = pg;
        this.mPwIsInvited    = pi;
        this.mPwIsUser       = pu;
        this.fPwWorldName    = pwn;
        this.fPwOwner        = pwo;
    }

    public boolean isAvailable() { return worldsManager != null; }

    // === Mundos creados ====================================================

    /** Devuelve nombres de TODOS los mundos gestionados por ETCWorlds. */
    @SuppressWarnings("unchecked")
    public Collection<String> getAllManagedNames() {
        if (!isAvailable()) return Collections.emptyList();
        try { return (Collection<String>) mGetManagedNames.invoke(worldsManager); }
        catch (Throwable t) { return Collections.emptyList(); }
    }

    /** Filtra los mundos que el jugador puede ver en el menú. */
    public List<WorldEntry> visibleWorldsFor(Player p, boolean bypass) {
        List<WorldEntry> out = new ArrayList<>();
        if (!isAvailable()) return out;
        for (String name : getAllManagedNames()) {
            // saltar pocketworlds (van en su propio menú)
            World w = Bukkit.getWorld(name);
            try {
                Object rules = mGetRules.invoke(worldsManager, name);
                if (rules == null) continue;

                boolean menuVisible  = readBoolField(rules, "menuVisible",  true);
                boolean publicAccess = readBoolField(rules, "publicAccess", true);
                boolean isTemplate   = readBoolField(rules, "isTemplate",   false);
                boolean perPlayerInst= readBoolField(rules, "perPlayerInstance", false);
                String accessPerm    = readStringField(rules, "accessPermission", "");
                @SuppressWarnings("unchecked")
                List<String> wl      = (List<String>) readObjField(rules, "whitelist", Collections.emptyList());

                // Excluir pocketworlds (carpeta /pocketworld/) e instancias por jugador
                if (perPlayerInst) continue;
                if (name.startsWith("pw_")) continue;

                if (!menuVisible && !bypass) continue;

                boolean canSee = publicAccess || bypass;
                if (!canSee && !accessPerm.isEmpty() && p.hasPermission(accessPerm)) canSee = true;
                if (!canSee && wl != null) {
                    String uuid = p.getUniqueId().toString();
                    String pname = p.getName();
                    for (String entry : wl) {
                        if (entry.equalsIgnoreCase(uuid) || entry.equalsIgnoreCase(pname)) { canSee = true; break; }
                    }
                }
                if (!canSee) continue;

                out.add(new WorldEntry(name, isTemplate, w != null));
            } catch (Throwable ignored) {}
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public Location getSpawn(String worldName) {
        World w = Bukkit.getWorld(worldName);
        return w == null ? null : w.getSpawnLocation();
    }

    // === PocketWorlds =====================================================

    public record PocketEntry(UUID owner, String worldName, String ownerName) {}

    /** Lista los pocketworlds que el jugador puede visitar (suyo + donde es invitee/user/admin). */
    @SuppressWarnings("unchecked")
    public List<PocketEntry> visiblePocketWorlds(Player p, boolean bypass) {
        List<PocketEntry> out = new ArrayList<>();
        if (!isAvailable()) return out;
        try {
            Collection<Object> all = (Collection<Object>) mPwAll.invoke(pocketManager);
            for (Object pw : all) {
                UUID owner = (UUID) fPwOwner.get(pw);
                String worldName = (String) fPwWorldName.get(pw);
                boolean canSee = bypass
                        || owner.equals(p.getUniqueId())
                        || (Boolean) mPwIsInvited.invoke(pocketManager, owner, p.getUniqueId())
                        || (Boolean) mPwIsUser.invoke(pocketManager, owner, p.getUniqueId());
                if (!canSee) continue;
                String ownerName = Bukkit.getOfflinePlayer(owner).getName();
                if (ownerName == null) ownerName = owner.toString().substring(0, 8);
                out.add(new PocketEntry(owner, worldName, ownerName));
            }
        } catch (Throwable ignored) {}
        out.sort((a, b) -> {
            // El propio primero
            if (a.owner.equals(p.getUniqueId())) return -1;
            if (b.owner.equals(p.getUniqueId())) return 1;
            return a.ownerName.compareToIgnoreCase(b.ownerName);
        });
        return out;
    }

    /** ¿El jugador tiene un PocketWorld? */
    public boolean hasOwnPocketWorld(UUID uuid) {
        if (!isAvailable()) return false;
        try { return mPwGet.invoke(pocketManager, uuid) != null; }
        catch (Throwable t) { return false; }
    }

    // === Reflection helpers ================================================

    private static boolean readBoolField(Object obj, String name, boolean def) {
        try { return obj.getClass().getField(name).getBoolean(obj); }
        catch (Throwable t) { return def; }
    }
    private static String readStringField(Object obj, String name, String def) {
        try {
            Object v = obj.getClass().getField(name).get(obj);
            return v == null ? def : v.toString();
        } catch (Throwable t) { return def; }
    }
    private static Object readObjField(Object obj, String name, Object def) {
        try { return obj.getClass().getField(name).get(obj); }
        catch (Throwable t) { return def; }
    }

    public record WorldEntry(String name, boolean template, boolean loaded) {}
}
