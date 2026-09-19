package com.jedislite.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisStoreListTest {

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
    @DisplayName("LPUSH and RPUSH element ordering")
    void testLpushAndRpushOrdering() {
        ByteArrayKey lKey = k("lList");
        int len1 = store.lpush(lKey, List.of(b("a"), b("b"), b("c")));
        assertThat(len1).isEqualTo(3);

        List<byte[]> lItems = store.lrange(lKey, 0, -1);
        assertThat(lItems).containsExactly(b("c"), b("b"), b("a"));

        ByteArrayKey rKey = k("rList");
        int len2 = store.rpush(rKey, List.of(b("a"), b("b"), b("c")));
        assertThat(len2).isEqualTo(3);

        List<byte[]> rItems = store.lrange(rKey, 0, -1);
        assertThat(rItems).containsExactly(b("a"), b("b"), b("c"));
    }

    @Test
    @DisplayName("LPOP and RPOP with auto-eviction when empty")
    void testPopAndAutoEviction() {
        ByteArrayKey key = k("myList");
        store.rpush(key, List.of(b("one"), b("two"), b("three")));

        assertThat(store.lpop(key)).isEqualTo(b("one"));
        assertThat(store.llen(key)).isEqualTo(2);

        assertThat(store.rpop(key)).isEqualTo(b("three"));
        assertThat(store.llen(key)).isEqualTo(1);

        assertThat(store.lpop(key)).isEqualTo(b("two"));

        assertThat(store.exists(key)).isFalse();
        assertThat(store.type(key)).isEqualTo(RedisDataType.NONE);
        assertThat(store.lpop(key)).isNull();
    }

    @Test
    @DisplayName("LRANGE with negative and boundary indices")
    void testLrangeIndices() {
        ByteArrayKey key = k("letters");
        store.rpush(key, List.of(b("A"), b("B"), b("C"), b("D"), b("E")));

        assertThat(store.lrange(key, 0, -1))
                .containsExactly(b("A"), b("B"), b("C"), b("D"), b("E"));

        assertThat(store.lrange(key, 1, 3))
                .containsExactly(b("B"), b("C"), b("D"));

        assertThat(store.lrange(key, -3, -1))
                .containsExactly(b("C"), b("D"), b("E"));

        assertThat(store.lrange(key, 3, 100))
                .containsExactly(b("D"), b("E"));

        assertThat(store.lrange(key, 3, 1)).isEmpty();

        assertThat(store.lrange(key, 10, 20)).isEmpty();
    }

    @Test
    @DisplayName("LINDEX with positive and negative indices")
    void testLindex() {
        ByteArrayKey key = k("indexed");
        store.rpush(key, List.of(b("first"), b("second"), b("third")));

        assertThat(store.lindex(key, 0)).isEqualTo(b("first"));
        assertThat(store.lindex(key, 1)).isEqualTo(b("second"));
        assertThat(store.lindex(key, -1)).isEqualTo(b("third"));
        assertThat(store.lindex(key, -3)).isEqualTo(b("first"));

        assertThat(store.lindex(key, 5)).isNull();
        assertThat(store.lindex(key, -5)).isNull();
    }

    @Test
    @DisplayName("List operations on non-list throw WrongTypeException")
    void testWrongTypeThrows() {
        ByteArrayKey key = k("strKey");
        store.set(key, b("string"));

        assertThatThrownBy(() -> store.lpush(key, List.of(b("x"))))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.rpush(key, List.of(b("x"))))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.lpop(key))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.rpop(key))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.llen(key))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.lrange(key, 0, -1))
                .isInstanceOf(WrongTypeException.class);
        assertThatThrownBy(() -> store.lindex(key, 0))
                .isInstanceOf(WrongTypeException.class);
    }
}

