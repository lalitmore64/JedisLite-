package com.jedislite.engine.command.impl;

import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;
import com.jedislite.storage.ByteArrayKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HashCommands {

    private HashCommands() {
    }

    public static void register(CommandRegistry registry) {

        registry.register("HSET", 3, -1, (args, db) -> {
            if (args.size() % 2 == 0) {
                return RespValue.err("wrong number of arguments for 'hset' command");
            }
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            Map<ByteArrayKey, byte[]> fieldValues = new LinkedHashMap<>();
            for (int i = 1; i < args.size(); i += 2) {
                ByteArrayKey field = CommandRegistry.toKey(args.get(i));
                byte[] val = CommandRegistry.toBytes(args.get(i + 1));
                fieldValues.put(field, val);
            }
            return RespValue.integer(db.getStore().hset(key, fieldValues));
        });

        registry.register("HGET", 2, 2, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            ByteArrayKey field = CommandRegistry.toKey(args.get(1));
            byte[] val = db.getStore().hget(key, field);
            return val != null ? RespValue.bulkString(val) : RespValue.NULL_BULK_STRING;
        });

        registry.register("HGETALL", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            Map<ByteArrayKey, byte[]> map = db.getStore().hgetall(key);
            List<RespValue> elements = new ArrayList<>(map.size() * 2);
            for (Map.Entry<ByteArrayKey, byte[]> entry : map.entrySet()) {
                elements.add(RespValue.bulkString(entry.getKey().toBytes()));
                elements.add(RespValue.bulkString(entry.getValue()));
            }
            return RespValue.array(elements);
        });

        registry.register("HDEL", 2, -1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            List<ByteArrayKey> fields = CommandRegistry.toKeyList(args.subList(1, args.size()));
            return RespValue.integer(db.getStore().hdel(key, fields));
        });

        registry.register("HEXISTS", 2, 2, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            ByteArrayKey field = CommandRegistry.toKey(args.get(1));
            return RespValue.integer(db.getStore().hexists(key, field) ? 1 : 0);
        });

        registry.register("HLEN", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            return RespValue.integer(db.getStore().hlen(key));
        });

        registry.register("HKEYS", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            List<ByteArrayKey> keys = db.getStore().hkeys(key);
            List<RespValue> elements = new ArrayList<>(keys.size());
            for (ByteArrayKey k : keys) {
                elements.add(RespValue.bulkString(k.toBytes()));
            }
            return RespValue.array(elements);
        });

        registry.register("HVALS", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            List<byte[]> vals = db.getStore().hvals(key);
            List<RespValue> elements = new ArrayList<>(vals.size());
            for (byte[] v : vals) {
                elements.add(RespValue.bulkString(v));
            }
            return RespValue.array(elements);
        });
    }
}

