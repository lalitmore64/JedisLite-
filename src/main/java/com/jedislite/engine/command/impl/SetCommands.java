package com.jedislite.engine.command.impl;

import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;
import com.jedislite.storage.ByteArrayKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SetCommands {

    private SetCommands() {
    }

    public static void register(CommandRegistry registry) {

        registry.register("SADD", 2, -1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            List<ByteArrayKey> members = CommandRegistry.toKeyList(args.subList(1, args.size()));
            return RespValue.integer(db.getStore().sadd(key, members));
        });

        registry.register("SMEMBERS", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            Set<ByteArrayKey> set = db.getStore().smembers(key);
            List<RespValue> elements = new ArrayList<>(set.size());
            for (ByteArrayKey m : set) {
                elements.add(RespValue.bulkString(m.toBytes()));
            }
            return RespValue.array(elements);
        });

        registry.register("SREM", 2, -1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            List<ByteArrayKey> members = CommandRegistry.toKeyList(args.subList(1, args.size()));
            return RespValue.integer(db.getStore().srem(key, members));
        });

        registry.register("SISMEMBER", 2, 2, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            ByteArrayKey member = CommandRegistry.toKey(args.get(1));
            return RespValue.integer(db.getStore().sismember(key, member) ? 1 : 0);
        });

        registry.register("SCARD", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            return RespValue.integer(db.getStore().scard(key));
        });
    }
}

