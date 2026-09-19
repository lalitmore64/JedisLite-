package com.jedislite.persistence;

import com.jedislite.storage.ByteArrayKey;
import com.jedislite.storage.RedisObject;
import com.jedislite.storage.RedisStore;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RdbWriter {

    private RdbWriter() {
    }

    public static void write(RedisStore store, OutputStream outputStream) throws IOException {
        Objects.requireNonNull(store, "RedisStore cannot be null");
        Objects.requireNonNull(outputStream, "OutputStream cannot be null");

        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(outputStream));

        out.write(RdbConstants.MAGIC);
        out.writeInt(RdbConstants.VERSION);

        List<RedisStore.DumpEntry> entries = store.dumpAll();
        for (RedisStore.DumpEntry entry : entries) {
            writeEntry(out, entry);
        }

        out.writeByte(RdbConstants.OP_EOF);
        out.flush();
    }

    private static void writeEntry(DataOutputStream out, RedisStore.DumpEntry entry) throws IOException {

        if (entry.expireAtMillis() != null) {
            out.writeByte(RdbConstants.OP_EXPIRE_TIME_MS);
            out.writeLong(entry.expireAtMillis());
        }

        RedisObject obj = entry.object();
        switch (obj) {
            case RedisObject.StringObj s -> {
                out.writeByte(RdbConstants.TYPE_STRING);
                writeByteArray(out, entry.key().toBytes());
                writeByteArray(out, s.getValue());
            }
            case RedisObject.HashObj h -> {
                out.writeByte(RdbConstants.TYPE_HASH);
                writeByteArray(out, entry.key().toBytes());
                Map<ByteArrayKey, byte[]> fields = h.getAll();
                out.writeInt(fields.size());
                for (Map.Entry<ByteArrayKey, byte[]> f : fields.entrySet()) {
                    writeByteArray(out, f.getKey().toBytes());
                    writeByteArray(out, f.getValue());
                }
            }
            case RedisObject.ListObj l -> {
                out.writeByte(RdbConstants.TYPE_LIST);
                writeByteArray(out, entry.key().toBytes());
                List<byte[]> items = l.lrange(0, -1);
                out.writeInt(items.size());
                for (byte[] item : items) {
                    writeByteArray(out, item);
                }
            }
            case RedisObject.SetObj s -> {
                out.writeByte(RdbConstants.TYPE_SET);
                writeByteArray(out, entry.key().toBytes());
                Set<ByteArrayKey> members = s.smembers();
                out.writeInt(members.size());
                for (ByteArrayKey member : members) {
                    writeByteArray(out, member.toBytes());
                }
            }
        }
    }

    private static void writeByteArray(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        if (bytes.length > 0) {
            out.write(bytes);
        }
    }

    public static void writeToFileAtomically(RedisStore store, Path targetPath) throws IOException {
        Objects.requireNonNull(targetPath, "TargetPath cannot be null");
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = targetPath.resolveSibling(targetPath.getFileName().toString() + ".tmp");
        try (OutputStream fos = Files.newOutputStream(tempFile)) {
            write(store, fos);
        }

        Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}

