package com.jedislite.engine.command.impl;

import com.jedislite.engine.command.CommandDefinition;
import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;
import com.jedislite.storage.ByteArrayKey;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class StringCommands {

    private StringCommands() {
    }

    public static void register(CommandRegistry registry) {

        registry.register("GET", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            byte[] val = db.getStore().get(key);
            return val != null ? RespValue.bulkString(val) : RespValue.NULL_BULK_STRING;
        });

        registry.register("SET", 2, -1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            byte[] val = CommandRegistry.toBytes(args.get(1));

            Long expireAtMillis = null;
            boolean nx = false;
            boolean xx = false;

            for (int i = 2; i < args.size(); i++) {
                String opt = CommandRegistry.toUtf8String(args.get(i)).toUpperCase(Locale.ROOT);
                switch (opt) {
                    case "EX" -> {
                        if (++i >= args.size()) return RespValue.err("syntax error");
                        long sec;
                        try {
                            sec = Long.parseLong(CommandRegistry.toUtf8String(args.get(i)));
                        } catch (NumberFormatException e) {
                            return RespValue.err("value is not an integer or out of range");
                        }
                        expireAtMillis = System.currentTimeMillis() + (sec * 1000L);
                    }
                    case "PX" -> {
                        if (++i >= args.size()) return RespValue.err("syntax error");
                        long ms;
                        try {
                            ms = Long.parseLong(CommandRegistry.toUtf8String(args.get(i)));
                        } catch (NumberFormatException e) {
                            return RespValue.err("value is not an integer or out of range");
                        }
                        expireAtMillis = System.currentTimeMillis() + ms;
                    }
                    case "NX" -> nx = true;
                    case "XX" -> xx = true;
                    default -> {
                        return RespValue.err("syntax error");
                    }
                }
            }

            if (nx && xx) {
                return RespValue.err("syntax error");
            }

            boolean applied = db.getStore().set(key, val, expireAtMillis, nx, xx);
            return applied ? RespValue.OK : RespValue.NULL_BULK_STRING;
        });

        registry.register("INCR", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            long newVal = db.getStore().incrBy(key, 1L);
            return RespValue.integer(newVal);
        });

        registry.register("DECR", 1, 1, (args, db) -> {
            ByteArrayKey key = CommandRegistry.toKey(args.get(0));
            long newVal = db.getStore().incrBy(key, -1L);
            return RespValue.integer(newVal);
        });
    }
}

