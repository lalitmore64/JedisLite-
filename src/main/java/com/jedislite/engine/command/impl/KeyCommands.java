package com.jedislite.engine.command.impl;

import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;
import com.jedislite.storage.ByteArrayKey;
import com.jedislite.storage.RedisDataType;

import java.util.List;

public final class KeyCommands {

    private KeyCommands() {
    }

    public static void register(CommandRegistry registry) {

        registry.register("DEL", 1, -1, (args, db) -> {
            List<ByteArrayKey> keys = CommandRegistry.toKeyList(args);
            return RespValue.integer(db.getStore().del(keys));
        });

        registry.register("EXISTS", 1, -1, (args, db) -> {
            List<ByteArrayKey> keys = CommandRegistry.toKeyList(args);
            return RespValue.integer(db.getStore().exists(keys));
        });

        registry.register("EXPIRE", 2, 2, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            long seconds;
            try {
                seconds = Long.parseLong(CommandRegistry.toUtf8String(args.get(1)));
            } catch (NumberFormatException e) {
                return RespValue.err("value is not an integer or out of range");
            }
            boolean success = db.getStore().expire(key, seconds);
            return RespValue.integer(success ? 1 : 0);
        });

        registry.register("TTL", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            long remaining = db.getStore().ttl(key);
            return RespValue.integer(remaining);
        });

        registry.register("TYPE", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            RedisDataType type = db.getStore().type(key);
            return RespValue.simpleString(type.typeName());
        });
    }
}

