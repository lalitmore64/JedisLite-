package com.jedislite.persistence;

import com.jedislite.storage.RedisStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SnapshotManager implements AutoCloseable {

    public static final Path DEFAULT_SNAPSHOT_PATH = Path.of("dump.rdb");
    public static final long DEFAULT_PERIODIC_SECONDS = 300;

    private final RedisStore store;
    private final Path snapshotPath;
    private final long periodicSeconds;
    private final AtomicBoolean saving = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;

    public SnapshotManager(RedisStore store, Path snapshotPath, long periodicSeconds) {
        this.store = Objects.requireNonNull(store, "RedisStore cannot be null");
        this.snapshotPath = Objects.requireNonNull(snapshotPath, "SnapshotPath cannot be null");
        this.periodicSeconds = periodicSeconds;
    }

    public SnapshotManager(RedisStore store, Path snapshotPath) {
        this(store, snapshotPath, DEFAULT_PERIODIC_SECONDS);
    }

    public SnapshotManager(RedisStore store) {
        this(store, DEFAULT_SNAPSHOT_PATH, DEFAULT_PERIODIC_SECONDS);
    }

    public int loadIfExists() throws IOException {
        return RdbReader.readFromFile(store, snapshotPath);
    }

    public void save() throws IOException {
        if (!saving.compareAndSet(false, true)) {
            throw new IllegalStateException("A save operation is already in progress");
        }
        try {
            RdbWriter.writeToFileAtomically(store, snapshotPath);
        } finally {
            saving.set(false);
        }
    }

    public CompletableFuture<Void> bgsave() {
        if (!saving.compareAndSet(false, true)) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Background save already in progress"));
            return failed;
        }

        return CompletableFuture.runAsync(() -> {
            try {
                RdbWriter.writeToFileAtomically(store, snapshotPath);
            } catch (IOException e) {
                throw new RuntimeException("Background save failed: " + e.getMessage(), e);
            } finally {
                saving.set(false);
            }
        });
    }

    public synchronized void startPeriodicSave() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rdb-periodic-save");
                t.setDaemon(true);
                return t;
            });

            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    bgsave().join();
                } catch (Exception e) {
                    System.err.println("Error during periodic RDB snapshot: " + e.getMessage());
                }
            }, periodicSeconds, periodicSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isSaving() {
        return saving.get();
    }

    public Path getSnapshotPath() {
        return snapshotPath;
    }

    @Override
    public synchronized void close() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}

