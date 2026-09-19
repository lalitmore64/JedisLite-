package com.jedislite.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveExpirationSweeperTest {

    private RedisStore store;

    @BeforeEach
    void setUp() {
        store = new RedisStore();
    }

    private byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private ByteArrayKey k(String s) {
        return ByteArrayKey.of(s);
    }

    @Test
    @DisplayName("Deterministic sweepOnce purges dead keys without reading them (preventing memory leaks)")
    void testDeterministicSweepPurgesDeadKeys() {
        ActiveExpirationSweeper sweeper = new ActiveExpirationSweeper(store);

        for (int i = 0; i < 50; i++) {
            ByteArrayKey key = k("dead:" + i);
            store.set(key, b("value-" + i), System.currentTimeMillis() - 1000L, false, false);
        }

        for (int i = 0; i < 20; i++) {
            ByteArrayKey key = k("alive:" + i);
            store.set(key, b("value-" + i), System.currentTimeMillis() + 3600_000L, false, false);
        }

        assertThat(store.size()).isEqualTo(70);
        assertThat(store.getExpiringKeysCount()).isEqualTo(70);

        int totalPurged = 0;
        while (store.size() > 20) {
            totalPurged += sweeper.sweepOnce();
        }

        assertThat(totalPurged).isEqualTo(50);
        assertThat(store.size()).isEqualTo(20);
        assertThat(store.getExpiringKeysCount()).isEqualTo(20);

        for (int i = 0; i < 20; i++) {
            assertThat(store.exists(k("alive:" + i))).isTrue();
        }
    }

    @Test
    @DisplayName("Adaptive loop continues sweeping across multiple batches when > 25% sampled keys are expired")
    void testAdaptiveSamplingLoops() {

        ActiveExpirationSweeper sweeper = new ActiveExpirationSweeper(store, 100, 20, 100);

        for (int i = 0; i < 100; i++) {
            store.set(k("batch:" + i), b("val"), System.currentTimeMillis() - 500L, false, false);
        }

        assertThat(store.getExpiringKeysCount()).isEqualTo(100);

        int purged = sweeper.sweepOnce();

        assertThat(purged).isEqualTo(100);
        assertThat(store.size()).isEqualTo(0);
        assertThat(store.getExpiringKeysCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Background thread automatically sweeps expired keys over time")
    void testBackgroundSweeperThread() throws Exception {
        ActiveExpirationSweeper sweeper = new ActiveExpirationSweeper(store, 20, 20, 25);
        sweeper.start();
        assertThat(sweeper.isRunning()).isTrue();

        try {

            for (int i = 0; i < 15; i++) {
                store.set(k("auto:" + i), b("val"), System.currentTimeMillis() + 50L, false, false);
            }

            assertThat(store.size()).isEqualTo(15);

            Thread.sleep(200);

            assertThat(store.size()).isEqualTo(0);
            assertThat(store.getExpiringKeysCount()).isEqualTo(0);
        } finally {
            sweeper.stop();
            assertThat(sweeper.isRunning()).isFalse();
        }
    }
}

