package com.jedislite.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisStoreIncrDecrTest {

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
    @DisplayName("INCR and DECR initialize non-existent key to 0 before mutating")
    void testIncrDecrMissingKey() {
        long res1 = store.incrBy(k("c1"), 1);
        assertThat(res1).isEqualTo(1L);
        assertThat(store.get(k("c1"))).isEqualTo(b("1"));

        long res2 = store.incrBy(k("c2"), -1);
        assertThat(res2).isEqualTo(-1L);
        assertThat(store.get(k("c2"))).isEqualTo(b("-1"));
    }

    @Test
    @DisplayName("Sequential INCR and DECR mutations")
    void testSequentialMutations() {
        ByteArrayKey key = k("counter");
        store.set(key, b("10"));

        assertThat(store.incrBy(key, 1)).isEqualTo(11L);
        assertThat(store.incrBy(key, 1)).isEqualTo(12L);
        assertThat(store.incrBy(key, -1)).isEqualTo(11L);
        assertThat(store.incrBy(key, -11)).isEqualTo(0L);
        assertThat(store.incrBy(key, -1)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("INCR preserves key TTL")
    void testIncrPreservesTtl() {
        ByteArrayKey key = k("ttlCounter");
        store.set(key, b("100"));
        store.expire(key, 300);

        long initialTtl = store.ttl(key);
        assertThat(initialTtl).isGreaterThan(0);

        store.incrBy(key, 1);
        long afterIncrTtl = store.ttl(key);

        assertThat(afterIncrTtl).isGreaterThan(0);
        assertThat(store.get(key)).isEqualTo(b("101"));
    }

    @Test
    @DisplayName("INCR on non-integer string throws NumberFormatException")
    void testNonIntegerThrows() {
        ByteArrayKey key = k("str");
        store.set(key, b("hello world"));

        assertThatThrownBy(() -> store.incrBy(key, 1))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("value is not an integer or out of range");
    }

    @Test
    @DisplayName("INCR on non-string type throws WrongTypeException")
    void testWrongTypeThrows() {
        ByteArrayKey key = k("list");
        store.lpush(key, List.of(b("10")));

        assertThatThrownBy(() -> store.incrBy(key, 1))
                .isInstanceOf(WrongTypeException.class);
    }

    @Test
    @DisplayName("INCR overflow and DECR underflow throw ArithmeticException")
    void testOverflowAndUnderflowThrows() {
        ByteArrayKey maxKey = k("max");
        store.set(maxKey, Long.toString(Long.MAX_VALUE).getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> store.incrBy(maxKey, 1))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("increment or decrement would overflow");

        ByteArrayKey minKey = k("min");
        store.set(minKey, Long.toString(Long.MIN_VALUE).getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> store.incrBy(minKey, -1))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("increment or decrement would overflow");
    }
}

