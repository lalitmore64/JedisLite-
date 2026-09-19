package com.jedislite.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisStoreHashTest {

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
    @DisplayName("HSET and HGET basic operations")
    void testHsetAndHget() {
        ByteArrayKey key = k("user:100");
        Map<ByteArrayKey, byte[]> map = new LinkedHashMap<>();
        map.put(k("name"), b("Alice"));
        map.put(k("email"), b("alice@example.com"));

        int added = store.hset(key, map);
        assertThat(added).isEqualTo(2);
        assertThat(store.type(key)).isEqualTo(RedisDataType.HASH);

        assertThat(store.hget(key, k("name"))).isEqualTo(b("Alice"));
        assertThat(store.hget(key, k("email"))).isEqualTo(b("alice@example.com"));
        assertThat(store.hget(key, k("missing"))).isNull();

        int updated = store.hset(key, Map.of(k("name"), b("Alice Smith")));
        assertThat(updated).isEqualTo(0);
        assertThat(store.hget(key, k("name"))).isEqualTo(b("Alice Smith"));
    }

    @Test
    @DisplayName("HEXISTS, HLEN, HKEYS, HVALS, HGETALL")
    void testHashInspectionMethods() {
        ByteArrayKey key = k("myhash");
        Map<ByteArrayKey, byte[]> map = new LinkedHashMap<>();
        map.put(k("f1"), b("v1"));
        map.put(k("f2"), b("v2"));
        map.put(k("f3"), b("v3"));
        store.hset(key, map);

        assertThat(store.hexists(key, k("f1"))).isTrue();
        assertThat(store.hexists(key, k("f4"))).isFalse();
        assertThat(store.hlen(key)).isEqualTo(3);

        assertThat(store.hkeys(key)).containsExactly(k("f1"), k("f2"), k("f3"));
        assertThat(store.hvals(key)).containsExactly(b("v1"), b("v2"), b("v3"));

        Map<ByteArrayKey, byte[]> all = store.hgetall(key);
        assertThat(all).containsOnlyKeys(k("f1"), k("f2"), k("f3"));
        assertThat(all.get(k("f1"))).isEqualTo(b("v1"));
    }

    @Test
    @DisplayName("HDEL removes fields and auto-deletes key when empty")
    void testHdelAndAutoEviction() {
        ByteArrayKey key = k("hkey");
        store.hset(key, Map.of(k("f1"), b("v1"), k("f2"), b("v2")));

        int removed1 = store.hdel(key, List.of(k("f1"), k("missing")));
        assertThat(removed1).isEqualTo(1);
        assertThat(store.exists(key)).isTrue();
        assertThat(store.hlen(key)).isEqualTo(1);

        int removed2 = store.hdel(key, List.of(k("f2")));
        assertThat(removed2).isEqualTo(1);

        assertThat(store.exists(key)).isFalse();
        assertThat(store.type(key)).isEqualTo(RedisDataType.NONE);
        assertThat(store.hgetall(key)).isEmpty();
    }

    @Test
    @DisplayName("Hash operations on non-hash key throw WrongTypeException")
    void testWrongTypeThrows() {
        ByteArrayKey key = k("strKey");
        store.set(key, b("plainString"));

        assertThatThrownBy(() -> store.hset(key, Map.of(k("f"), b("v"))))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.hget(key, k("f")))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.hdel(key, List.of(k("f"))))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.hexists(key, k("f")))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.hlen(key))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.hgetall(key))
                .isInstanceOf(WrongTypeException.class);
    }
}

