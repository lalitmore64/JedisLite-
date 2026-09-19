package com.jedislite.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisStoreStringTest {

    private RedisStore store;

    @BeforeEach
    void setUp() {
        store = new RedisStore();
    }

    private byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("SET and GET string values")
    void testSetAndGet() {
        store.set(b("user:1"), b("Alice"));
        assertThat(store.get(b("user:1"))).isEqualTo(b("Alice"));
        assertThat(store.type(b("user:1"))).isEqualTo(RedisDataType.STRING);

        assertThat(store.get(b("missing"))).isNull();
        assertThat(store.type(b("missing"))).isEqualTo(RedisDataType.NONE);
    }

    @Test
    @DisplayName("SET overwrites previous non-string types")
    void testSetOverwritesDifferentType() {

        store.lpush(ByteArrayKey.of("mykey"), List.of(b("val1"), b("val2")));
        assertThat(store.type(b("mykey"))).isEqualTo(RedisDataType.LIST);

        store.set(b("mykey"), b("newString"));
        assertThat(store.type(b("mykey"))).isEqualTo(RedisDataType.STRING);
        assertThat(store.get(b("mykey"))).isEqualTo(b("newString"));
    }

    @Test
    @DisplayName("GET on non-string key throws WrongTypeException")
    void testGetOnNonStringThrows() {
        store.lpush(ByteArrayKey.of("listKey"), List.of(b("item")));
        assertThatThrownBy(() -> store.get(b("listKey")))
                .isInstanceOf(WrongTypeException.class);
    }

    @Test
    @DisplayName("DEL and EXISTS for strings")
    void testDelAndExists() {
        store.set(b("k1"), b("v1"));
        assertThat(store.exists(b("k1"))).isTrue();
        assertThat(store.del(b("k1"))).isTrue();
        assertThat(store.exists(b("k1"))).isFalse();
        assertThat(store.del(b("k1"))).isFalse();
    }
}

