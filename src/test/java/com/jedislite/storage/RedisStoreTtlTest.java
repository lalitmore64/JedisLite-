package com.jedislite.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStoreTtlTest {

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
    @DisplayName("TTL on non-existent key returns -2")
    void testTtlNonExistent() {
        assertThat(store.ttl(k("missing"))).isEqualTo(-2L);
    }

    @Test
    @DisplayName("TTL on key without expiration returns -1")
    void testTtlNoExpiration() {
        store.set(k("k1"), b("v1"));
        assertThat(store.ttl(k("k1"))).isEqualTo(-1L);
    }

    @Test
    @DisplayName("EXPIRE sets TTL and returns remaining seconds")
    void testExpireAndTtl() {
        store.set(k("k1"), b("v1"));
        boolean expired = store.expire(k("k1"), 100);
        assertThat(expired).isTrue();

        long remaining = store.ttl(k("k1"));
        assertThat(remaining).isBetween(95L, 100L);
    }

    @Test
    @DisplayName("EXPIRE on non-existent key returns false")
    void testExpireNonExistent() {
        boolean expired = store.expire(k("missing"), 60);
        assertThat(expired).isFalse();
    }

    @Test
    @DisplayName("EXPIRE with 0 or negative seconds deletes key immediately")
    void testExpireZeroOrNegativeDeletesKey() {
        store.set(k("k1"), b("v1"));
        boolean expired = store.expire(k("k1"), 0);
        assertThat(expired).isTrue();
        assertThat(store.exists(k("k1"))).isFalse();
        assertThat(store.get(k("k1"))).isNull();
        assertThat(store.ttl(k("k1"))).isEqualTo(-2L);
    }

    @Test
    @DisplayName("Passive eviction: expired key is lazily removed on access")
    void testPassiveEviction() throws InterruptedException {
        store.set(k("temp"), b("val"));

        store.pexpire(k("temp"), 50);

        assertThat(store.exists(k("temp"))).isTrue();
        assertThat(store.get(k("temp"))).isEqualTo(b("val"));

        Thread.sleep(80);

        assertThat(store.get(k("temp"))).isNull();
        assertThat(store.exists(k("temp"))).isFalse();
        assertThat(store.ttl(k("temp"))).isEqualTo(-2L);
    }

    @Test
    @DisplayName("Plain SET removes existing TTL")
    void testSetClearsTtl() {
        store.set(k("k1"), b("v1"));
        store.expire(k("k1"), 100);
        assertThat(store.ttl(k("k1"))).isGreaterThan(0);

        store.set(k("k1"), b("v2"));
        assertThat(store.ttl(k("k1"))).isEqualTo(-1L);
    }
}

