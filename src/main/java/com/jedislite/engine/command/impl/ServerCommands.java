package com.jedislite.engine.command.impl;

import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;

import java.io.IOException;

public final class ServerCommands {

    private ServerCommands() {
    }

    public static void register(CommandRegistry registry) {

        registry.register("PING", 0, 1, (args, db) -> {
            if (args.isEmpty()) {
                return RespValue.PONG;
            }
            return RespValue.bulkString(CommandRegistry.toBytes(args.get(0)));
        });

        registry.register("ECHO", 1, 1, (args, db) ->
                RespValue.bulkString(CommandRegistry.toBytes(args.get(0)))
        );

        registry.register("COMMAND", 0, -1, (args, db) -> RespValue.EMPTY_ARRAY);

        registry.register("SAVE", 0, 0, (args, db) -> {
            try {
                db.getSnapshotManager().save();
                return RespValue.OK;
            } catch (IOException e) {
                return RespValue.err("SAVE failed: " + e.getMessage());
            }
        });

        registry.register("BGSAVE", 0, 0, (args, db) -> {
            try {
                db.getSnapshotManager().bgsave();
                return RespValue.simpleString("Background saving started");
            } catch (IllegalStateException e) {
                return RespValue.err(e.getMessage());
            }
        });

        registry.register("CLIENT", 0, -1, (args, db) -> RespValue.OK);

        registry.register("CONFIG", 1, -1, (args, db) -> RespValue.EMPTY_ARRAY);

        registry.register("INFO", 0, 1, (args, db) -> {
            String info = "# Server\r\n" +
                    "redis_version:7.0.0-jedislite\r\n" +
                    "redis_mode:standalone\r\n" +
                    "os:Windows\r\n" +
                    "arch_bits:64\r\n" +
                    "tcp_port:6379\r\n" +
                    "# Stats\r\n" +
                    "total_keys:" + db.getStore().size() + "\r\n";
            return RespValue.bulkString(info);
        });
    }
}

