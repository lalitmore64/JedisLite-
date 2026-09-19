package com.jedislite.engine.command.impl;

import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;
import com.jedislite.storage.ByteArrayKey;

import java.util.ArrayList;
import java.util.List;

public final class ListCommands {

    private ListCommands() {
    }

    public static void register(CommandRegistry registry) {

        registry.register("LPUSH", 2, -1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            List<byte[]> values = CommandRegistry.toByteArrayList(args.subList(1, args.size()));
            return RespValue.integer(db.getStore().lpush(key, values));
        });

        registry.register("RPUSH", 2, -1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            List<byte[]> values = CommandRegistry.toByteArrayList(args.subList(1, args.size()));
            return RespValue.integer(db.getStore().rpush(key, values));
        });

        registry.register("LRANGE", 3, 3, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            int start;
            int stop;
            try {
                start = Integer.parseInt(CommandRegistry.toUtf8String(args.get(1)));
                stop = Integer.parseInt(CommandRegistry.toUtf8String(args.get(2)));
            } catch (NumberFormatException e) {
                return RespValue.err("value is not an integer or out of range");
            }
            List<byte[]> items = db.getStore().lrange(key, start, stop);
            List<RespValue> elements = new ArrayList<>(items.size());
            for (byte[] item : items) {
                elements.add(RespValue.bulkString(item));
            }
            return RespValue.array(elements);
        });

        registry.register("LPOP", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            byte[] popped = db.getStore().lpop(key);
            return popped != null ? RespValue.bulkString(popped) : RespValue.NULL_BULK_STRING;
        });

        registry.register("RPOP", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            byte[] popped = db.getStore().rpop(key);
            return popped != null ? RespValue.bulkString(popped) : RespValue.NULL_BULK_STRING;
        });

        registry.register("LLEN", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            return RespValue.integer(db.getStore().llen(key));
        });

        registry.register("LINDEX", 2, 2, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            int index;
            try {
                index = Integer.parseInt(CommandRegistry.toUtf8String(args.get(1)));
            } catch (NumberFormatException e) {
                return RespValue.err("value is not an integer or out of range");
            }
            byte[] item = db.getStore().lindex(key, index);
            return item != null ? RespValue.bulkString(item) : RespValue.NULL_BULK_STRING;
        });
    }
}

