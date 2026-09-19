package com.jedislite.engine;

import com.jedislite.persistence.SnapshotManager;
import com.jedislite.storage.ByteArrayKey;
import com.jedislite.storage.RedisStore;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Database {

    private final RedisStore store;
    private final SnapshotManager snapshotManager;

    public Database(RedisStore store, SnapshotManager snapshotManager) {
        this.store = Objects.requireNonNull(store, "RedisStore cannot be null");
        this.snapshotManager = snapshotManager != null ? snapshotManager : new SnapshotManager(store);
    }

    public Database(RedisStore store) {
        this(store, new SnapshotManager(store));
    }

    public Database() {
        this(new RedisStore());
    }

    public RedisStore getStore() {
        return store;
    }

    public SnapshotManager getSnapshotManager() {
        return snapshotManager;
    }

    public void set(byte[] key, byte[] value) {
        store.set(key, value);
    }

    public void set(String key, String value) {
        set(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] get(byte[] key) {
        return store.get(key);
    }

    public String getString(String key) {
        byte[] val = get(key.getBytes(StandardCharsets.UTF_8));
        return val != null ? new String(val, StandardCharsets.UTF_8) : null;
    }

    public boolean del(byte[] key) {
        return store.del(key);
    }

    public boolean del(String key) {
        return del(key.getBytes(StandardCharsets.UTF_8));
    }

    public boolean exists(byte[] key) {
        return store.exists(key);
    }

    public boolean exists(String key) {
        return exists(key.getBytes(StandardCharsets.UTF_8));
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}

