package com.jedislite.storage;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActiveExpirationSweeper implements AutoCloseable {

    public static final long DEFAULT_INTERVAL_MILLIS = 100;
    public static final int DEFAULT_SAMPLE_SIZE = 20;
    public static final long DEFAULT_TIME_BUDGET_MILLIS = 25;

    private final RedisStore store;
    private final long intervalMillis;
    private final int sampleSize;
    private final long timeBudgetMillis;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread sweepThread;

    public ActiveExpirationSweeper(RedisStore store, long intervalMillis, int sampleSize, long timeBudgetMillis) {
        this.store = Objects.requireNonNull(store, "RedisStore cannot be null");
        this.intervalMillis = intervalMillis;
        this.sampleSize = sampleSize;
        this.timeBudgetMillis = timeBudgetMillis;
    }

    public ActiveExpirationSweeper(RedisStore store) {
        this(store, DEFAULT_INTERVAL_MILLIS, DEFAULT_SAMPLE_SIZE, DEFAULT_TIME_BUDGET_MILLIS);
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            sweepThread = Thread.ofPlatform()
                    .daemon()
                    .name("active-expire-sweeper")
                    .start(this::runSweepLoop);
        }
    }

    public int sweepOnce() {
        return store.activeExpireCycle(sampleSize, timeBudgetMillis);
    }

    private void runSweepLoop() {
        while (running.get()) {
            try {
                sweepOnce();
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("Unexpected error during active expiration sweep: " + e.getMessage());
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (sweepThread != null) {
                sweepThread.interrupt();
                try {
                    sweepThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                sweepThread = null;
            }
        }
    }

    @Override
    public void close() {
        stop();
    }
}

