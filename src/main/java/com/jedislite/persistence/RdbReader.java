package com.jedislite.persistence;

import com.jedislite.storage.ByteArrayKey;
import com.jedislite.storage.RedisObject;
import com.jedislite.storage.RedisStore;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RdbReader {

    private RdbReader() {
    }

    public static int read(RedisStore store, InputStream in) throws IOException {
        Objects.requireNonNull(store, "RedisStore cannot be null");
        Objects.requireNonNull(in, "InputStream cannot be null");

        DataInputStream dis = new DataInputStream(new BufferedInputStream(in));

        byte[] magic = new byte[RdbConstants.MAGIC.length];
        dis.readFully(magic);
        if (!Arrays.equals(magic, RdbConstants.MAGIC)) {
            throw new IOException("Invalid RDB header: expected " + new String(RdbConstants.MAGIC) + " magic header");
        }

        int version = dis.readInt();
        if (version != RdbConstants.VERSION) {
            throw new IOException("Unsupported RDB version: " + version + " (expected " + RdbConstants.VERSION + ")");
        }

        int restoredCount = 0;
        while (true) {
            int tag = dis.read();
            if (tag == -1 || tag == (RdbConstants.OP_EOF & 0xFF)) {
                break;
            }

            Long expireAt = null;
            if (tag == (RdbConstants.OP_EXPIRE_TIME_MS & 0xFF)) {
                expireAt = dis.readLong();
                tag = dis.read();
                if (tag == -1) {
                    throw new IOException("Unexpected EOF after expiration opcode");
                }
            }

            ByteArrayKey key = ByteArrayKey.of(readByteArray(dis));
            RedisObject object = switch ((byte) tag) {
                case RdbConstants.TYPE_STRING -> {
                    byte[] val = readByteArray(dis);
                    yield new RedisObject.StringObj(val);
                }
                case RdbConstants.TYPE_HASH -> {
                    int count = dis.readInt();
                    RedisObject.HashObj hash = new RedisObject.HashObj();
                    Map<ByteArrayKey, byte[]> map = new LinkedHashMap<>(count);
                    for (int i = 0; i < count; i++) {
                        ByteArrayKey field = ByteArrayKey.of(readByteArray(dis));
                        byte[] val = readByteArray(dis);
                        map.put(field, val);
                    }
                    hash.set(map);
                    yield hash;
                }
                case RdbConstants.TYPE_LIST -> {
                    int count = dis.readInt();
                    RedisObject.ListObj list = new RedisObject.ListObj();
                    List<byte[]> items = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        items.add(readByteArray(dis));
                    }
                    list.rpush(items);
                    yield list;
                }
                case RdbConstants.TYPE_SET -> {
                    int count = dis.readInt();
                    RedisObject.SetObj set = new RedisObject.SetObj();
                    List<ByteArrayKey> members = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        members.add(ByteArrayKey.of(readByteArray(dis)));
                    }
                    set.sadd(members);
                    yield set;
                }
                default -> throw new IOException("Unknown RDB type opcode: 0x" + Integer.toHexString(tag));
            };

            if (expireAt == null || System.currentTimeMillis() < expireAt) {
                store.restore(key, object, expireAt);
                restoredCount++;
            }
        }

        return restoredCount;
    }

    private static byte[] readByteArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("Negative byte array length in RDB: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    public static int readFromFile(RedisStore store, Path path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        if (!Files.exists(path)) {
            return 0;
        }
        try (InputStream in = Files.newInputStream(path)) {
            return read(store, in);
        }
    }
}

