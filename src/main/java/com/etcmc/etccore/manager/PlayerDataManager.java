package com.etcmc.etccore.manager;

import com.etcmc.etccore.ETCCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacena variables por jugador en playerdata/<uuid>.yml dentro del
 * directorio de datos del plugin. Las variables son cadenas de texto
 * que pueden leerse y escribirse desde acciones [VAR:SET] / [VAR:ADD].
 *
 * Thread-safe: caché en memoria + guardado asíncrono en disco.
 */
public class PlayerDataManager {

    private final ETCCore plugin;
    private final File dataDir;

    // UUID → (variable → valor)
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>> cache
            = new ConcurrentHashMap<>();

    public PlayerDataManager(ETCCore plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists()) dataDir.mkdirs();
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /** Devuelve el valor de una variable o "" si no existe. */
    public String get(UUID uuid, String variable) {
        return load(uuid).getOrDefault(variable.toLowerCase(), "");
    }

    /**
     * Guarda una variable. El valor puede ser una expresión simple:
     *   "5"              → asigna "5"
     *   "{var:puntos} + 3" → suma 3 al valor numérico actual
     *   "{var:puntos} - 1" → resta 1
     * Si la expresión no es aritmética, se guarda como texto plano.
     */
    public void set(UUID uuid, String variable, String expression) {
        String key = variable.toLowerCase();
        String resolved = resolveExpression(uuid, expression);
        load(uuid).put(key, resolved);
        saveAsync(uuid);
    }

    /** Elimina una variable. */
    public void remove(UUID uuid, String variable) {
        ConcurrentHashMap<String, String> map = cache.get(uuid);
        if (map != null) {
            map.remove(variable.toLowerCase());
            saveAsync(uuid);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers tipados
    // -------------------------------------------------------------------------
    /** Devuelve un boolean con valor por defecto si la variable no existe. */
    public boolean getBool(UUID uuid, String variable, boolean def) {
        String v = get(uuid, variable);
        if (v == null || v.isEmpty()) return def;
        return Boolean.parseBoolean(v);
    }

    /** Guarda un boolean directamente (sin pasar por evalSimpleMath). */
    public void setBool(UUID uuid, String variable, boolean value) {
        load(uuid).put(variable.toLowerCase(), Boolean.toString(value));
        saveAsync(uuid);
    }

    /** Toggle: invierte el valor actual y devuelve el nuevo. */
    public boolean toggleBool(UUID uuid, String variable, boolean defaultIfMissing) {
        boolean newValue = !getBool(uuid, variable, defaultIfMissing);
        setBool(uuid, variable, newValue);
        return newValue;
    }

    // -------------------------------------------------------------------------
    // Evaluación de expresiones simples con {var:x}
    // -------------------------------------------------------------------------
    private String resolveExpression(UUID uuid, String expr) {
        // Reemplazar {var:nombre} por su valor actual
        String resolved = expr;
        while (resolved.contains("{var:")) {
            int start = resolved.indexOf("{var:");
            int end   = resolved.indexOf("}", start);
            if (end < 0) break;
            String varName = resolved.substring(start + 5, end);
            String val     = get(uuid, varName);
            resolved = resolved.substring(0, start) + val + resolved.substring(end + 1);
        }

        // Intentar evaluar aritmética simple: X + N  /  X - N  /  X * N  /  X / N
        resolved = resolved.trim();
        try {
            double result = evalSimpleMath(resolved);
            // Si es entero, devolver sin decimales
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return resolved; // texto plano
        }
    }

    /** Evalúa expresiones de UN solo operador: "a + b", "a - b", "a * b", "a / b". */
    private double evalSimpleMath(String expr) {
        for (String op : new String[]{"+", "-", "*", "/"}) {
            // Buscar el operador (evitar el signo del primer número)
            int idx = lastIndexOfOp(expr, op);
            if (idx > 0) {
                double left  = Double.parseDouble(expr.substring(0, idx).trim());
                double right = Double.parseDouble(expr.substring(idx + 1).trim());
                return switch (op) {
                    case "+" -> left + right;
                    case "-" -> left - right;
                    case "*" -> left * right;
                    case "/" -> left / right;
                    default  -> throw new IllegalArgumentException();
                };
            }
        }
        return Double.parseDouble(expr); // número directo
    }

    private int lastIndexOfOp(String expr, String op) {
        // Buscar el operador de derecha a izquierda para respetar precedencia básica
        return expr.lastIndexOf(" " + op + " ");
    }

    // -------------------------------------------------------------------------
    // Carga y guardado
    // -------------------------------------------------------------------------
    private ConcurrentHashMap<String, String> load(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
            File file = fileFor(id);
            if (file.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection vars = cfg.getConfigurationSection("vars");
                if (vars != null) {
                    for (String key : vars.getKeys(false)) {
                        map.put(key, vars.getString(key, ""));
                    }
                }
            }
            return map;
        });
    }

    private void saveAsync(UUID uuid) {
        if (!plugin.isEnabled()) {
            // El plugin ya está deshabilitado: el scheduler rechazaría la tarea.
            // Guardamos en el hilo actual (shutdown thread de Folia).
            saveSync(uuid);
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> saveSync(uuid));
    }

    private void saveSync(UUID uuid) {
        ConcurrentHashMap<String, String> map = cache.get(uuid);
        if (map == null) return;
        YamlConfiguration cfg = new YamlConfiguration();
        map.forEach((k, v) -> cfg.set("vars." + k, v));
        try {
            cfg.save(fileFor(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Error guardando datos de " + uuid + ": " + e.getMessage());
        }
    }

    private File fileFor(UUID uuid) {
        return new File(dataDir, uuid + ".yml");
    }
}
