package com.jedislite.persistence;

import com.jedislite.storage.ByteArrayKey;
import com.jedislite.storage.RedisDataType;
import com.jedislite.storage.RedisStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RdbPersistenceTest {

    private byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private ByteArrayKey k(String s) {
        return ByteArrayKey.of(s);
    }

    @Test
    @DisplayName("Round-trip RDB serialization saves and restores String, Hash, List, Set, and TTLs")
    void testRoundTripPersistence() throws IOException {
        RedisStore originalStore = new RedisStore();

        originalStore.set(k("str:plain"), b("hello world"));

        originalStore.set(k("str:ttl"), b("temporary"), System.currentTimeMillis() + 1_000_000L, false, false);

        originalStore.hset(k("user:profile"), Map.of(k("name"), b("Alice"), k("role"), b("admin")));

        originalStore.rpush(k("queue"), List.of(b("job1"), b("job2"), b("job3")));

        originalStore.sadd(k("tags"), List.of(k("java"), k("redis"), k("nio")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RdbWriter.write(originalStore, baos);
        byte[] rdbBytes = baos.toByteArray();

        assertThat(rdbBytes).startsWith(RdbConstants.MAGIC);

        RedisStore restoredStore = new RedisStore();
        int restoredCount = RdbReader.read(restoredStore, new ByteArrayInputStream(rdbBytes));

        assertThat(restoredCount).isEqualTo(5);
        assertThat(restoredStore.size()).isEqualTo(5);

        assertThat(restoredStore.get(k("str:plain"))).isEqualTo(b("hello world"));
        assertThat(restoredStore.ttl(k("str:plain"))).isEqualTo(-1L);

        assertThat(restoredStore.get(k("str:ttl"))).isEqualTo(b("temporary"));
        assertThat(restoredStore.ttl(k("str:ttl"))).isGreaterThan(500L);

        assertThat(restoredStore.type(k("user:profile"))).isEqualTo(RedisDataType.HASH);
        assertThat(restoredStore.hget(k("user:profile"), k("name"))).isEqualTo(b("Alice"));
        assertThat(restoredStore.hget(k("user:profile"), k("role"))).isEqualTo(b("admin"));

        assertThat(restoredStore.type(k("queue"))).isEqualTo(RedisDataType.LIST);
        assertThat(restoredStore.lrange(k("queue"), 0, -1))
                .containsExactly(b("job1"), b("job2"), b("job3"));

        assertThat(restoredStore.type(k("tags"))).isEqualTo(RedisDataType.SET);
        assertThat(restoredStore.smembers(k("tags")))
                .containsExactlyInAnyOrder(k("java"), k("redis"), k("nio"));
    }

    @Test
    @DisplayName("Expired keys are omitted during snapshotting and not restored")
    void testExpiredKeysOmitted() throws IOException {
        RedisStore store = new RedisStore();

        store.set(k("dead"), b("val"), System.currentTimeMillis() - 1000L, false, false);

        store.set(k("alive"), b("val"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RdbWriter.write(store, baos);

        RedisStore restored = new RedisStore();
        int count = RdbReader.read(restored, new ByteArrayInputStream(baos.toByteArray()));

        assertThat(count).isEqualTo(1);
        assertThat(restored.exists(k("dead"))).isFalse();
        assertThat(restored.exists(k("alive"))).isTrue();
    }

    @Test
    @DisplayName("Corrupted RDB magic header throws IOException")
    void testCorruptedHeader() {
        byte[] corrupted = "CORRUPT_MAGIC_1234".getBytes(StandardCharsets.UTF_8);
        RedisStore store = new RedisStore();

        assertThatThrownBy(() -> RdbReader.read(store, new ByteArrayInputStream(corrupted)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid RDB header");
    }

    @Test
    @DisplayName("Atomic file write creates target file and removes temporary file")
    void testAtomicFileWrite(@TempDir Path tempDir) throws IOException {
        RedisStore store = new RedisStore();
        store.set(k("k1"), b("v1"));

        Path dumpPath = tempDir.resolve("dump.rdb");
        RdbWriter.writeToFileAtomically(store, dumpPath);

        assertThat(Files.exists(dumpPath)).isTrue();
        Path tmpPath = tempDir.resolve("dump.rdb.tmp");
        assertThat(Files.exists(tmpPath)).isFalse();

        RedisStore restored = new RedisStore();
        int count = RdbReader.readFromFile(restored, dumpPath);
        assertThat(count).isEqualTo(1);
        assertThat(restored.get(k("k1"))).isEqualTo(b("v1"));
    }

    @Test
    @DisplayName("SnapshotManager save and bgsave operations")
    void testSnapshotManager(@TempDir Path tempDir) throws Exception {
        RedisStore store = new RedisStore();
        store.set(k("sync"), b("val1"));

        Path dumpPath = tempDir.resolve("snap.rdb");
        SnapshotManager manager = new SnapshotManager(store, dumpPath);

        manager.save();
        assertThat(Files.exists(dumpPath)).isTrue();

        store.set(k("async"), b("val2"));

        CompletableFuture<Void> bgFuture = manager.bgsave();
        bgFuture.get();

        RedisStore freshStore = new RedisStore();
        SnapshotManager reloadManager = new SnapshotManager(freshStore, dumpPath);
        int loaded = reloadManager.loadIfExists();

        assertThat(loaded).isEqualTo(2);
        assertThat(freshStore.get(k("sync"))).isEqualTo(b("val1"));
        assertThat(freshStore.get(k("async"))).isEqualTo(b("val2"));

        manager.close();
        reloadManager.close();
    }
}

