package com.jedislite.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisStoreSetTest {

    private RedisStore store;

    @BeforeEach
    void setUp() {
        store = new RedisStore();
    }

    private ByteArrayKey k(String s) {
        return ByteArrayKey.of(s);
    }

    @Test
    @DisplayName("SADD adds unique members and ignores duplicates")
    void testSaddAndDuplicates() {
        ByteArrayKey key = k("myset");
        int added1 = store.sadd(key, List.of(k("apple"), k("banana"), k("cherry")));
        assertThat(added1).isEqualTo(3);
        assertThat(store.scard(key)).isEqualTo(3);
        assertThat(store.type(key)).isEqualTo(RedisDataType.SET);

        int added2 = store.sadd(key, List.of(k("apple"), k("date")));
        assertThat(added2).isEqualTo(1);
        assertThat(store.scard(key)).isEqualTo(4);
    }

    @Test
    @DisplayName("SISMEMBER and SMEMBERS")
    void testSismemberAndSmembers() {
        ByteArrayKey key = k("colors");
        store.sadd(key, List.of(k("red"), k("green"), k("blue")));

        assertThat(store.sismember(key, k("red"))).isTrue();
        assertThat(store.sismember(key, k("yellow"))).isFalse();

        Set<ByteArrayKey> members = store.smembers(key);
        assertThat(members).containsExactlyInAnyOrder(k("red"), k("green"), k("blue"));
    }

    @Test
    @DisplayName("SREM removes members and auto-evicts key when empty")
    void testSremAndAutoEviction() {
        ByteArrayKey key = k("tags");
        store.sadd(key, List.of(k("java"), k("redis")));

        int removed1 = store.srem(key, List.of(k("java"), k("nonexistent")));
        assertThat(removed1).isEqualTo(1);
        assertThat(store.scard(key)).isEqualTo(1);
        assertThat(store.exists(key)).isTrue();

        int removed2 = store.srem(key, List.of(k("redis")));
        assertThat(removed2).isEqualTo(1);

        assertThat(store.exists(key)).isFalse();
        assertThat(store.type(key)).isEqualTo(RedisDataType.NONE);
        assertThat(store.smembers(key)).isEmpty();
    }

    @Test
    @DisplayName("Set operations on non-set key throw WrongTypeException")
    void testWrongTypeThrows() {
        ByteArrayKey key = k("strKey");
        store.set(key, "stringValue".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> store.sadd(key, List.of(k("m"))))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.srem(key, List.of(k("m"))))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.sismember(key, k("m")))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.smembers(key))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.scard(key))
                .isInstanceOf(WrongTypeException.class);
    }
}

