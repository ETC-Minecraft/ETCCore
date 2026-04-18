package com.etcmc.etccore.manager;

import com.etcmc.etccore.ETCCore;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hilo daemon que monitorea la carpeta commands/ con Java WatchService.
 * Cuando detecta un cambio en un .yml, programa un reload en el GlobalRegionScheduler.
 * Incluye debounce de 500 ms para evitar múltiples recargas por una sola edición.
 */
public class FileWatcher implements Runnable {

    private static final long DEBOUNCE_MS = 500;

    private final ETCCore plugin;
    private final Path watchDir;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong lastReloadAt = new AtomicLong(0);

    public FileWatcher(ETCCore plugin) {
        this.plugin = plugin;
        this.watchDir = plugin.getDataFolder().toPath().resolve("commands");
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        try {
            if (!watchDir.toFile().exists()) watchDir.toFile().mkdirs();

            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                watchDir.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                while (running.get()) {
                    WatchKey key = watcher.poll(1, TimeUnit.SECONDS);
                    if (key == null) continue;

                    boolean hasYmlChange = key.pollEvents().stream()
                            .anyMatch(e -> e.context() instanceof Path p
                                    && p.toString().endsWith(".yml"));
                    key.reset();

                    if (!hasYmlChange) continue;

                    // Debounce: ignorar si ya recargamos hace menos de 500 ms
                    long now = System.currentTimeMillis();
                    if (now - lastReloadAt.get() < DEBOUNCE_MS) continue;
                    lastReloadAt.set(now);

                    plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                        plugin.getCommandManager().reload();
                        plugin.getLogger().info("[Auto-reload] Cambio detectado en commands/ — recargado.");
                    });
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            plugin.getLogger().warning("FileWatcher error: " + e.getMessage());
        }
    }
}
