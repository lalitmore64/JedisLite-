package com.jedislite.engine;

import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;
import com.jedislite.storage.ByteArrayKey;
import com.jedislite.storage.RedisStore;

import java.util.List;
import java.util.Objects;

public class CommandDispatcher {

    private final Database database;
    private final CommandRegistry registry;

    public CommandDispatcher(Database database, CommandRegistry registry) {
        this.database = Objects.requireNonNull(database, "Database cannot be null");
        this.registry = Objects.requireNonNull(registry, "CommandRegistry cannot be null");
    }

    public CommandDispatcher(Database database) {
        this(database, new CommandRegistry());
    }

    public Database getDatabase() {
        return database;
    }

    public RedisStore getStore() {
        return database.getStore();
    }

    public CommandRegistry getRegistry() {
        return registry;
    }

    public void register(String commandName, Command command) {
        registry.register(commandName, 0, -1, command);
    }

    public RespValue dispatch(RespValue request) {
        if (!(request instanceof RespValue.Array array) || array.isNull() || array.size() == 0) {
            return RespValue.err("Protocol error: expected Array of command arguments");
        }

        RespValue cmdToken = array.get(0);
        String cmdName = CommandRegistry.toUtf8String(cmdToken);
        if (cmdName == null || cmdName.isEmpty()) {
            return RespValue.err("Protocol error: empty command name");
        }

        List<RespValue> args = array.elements().subList(1, array.size());
        return registry.execute(cmdName, args, database);
    }

    public static ByteArrayKey toKey(RespValue value) {
        return CommandRegistry.toKey(value);
    }

    public static List<ByteArrayKey> toKeyList(List<RespValue> values) {
        return CommandRegistry.toKeyList(values);
    }

    public static List<byte[]> toByteArrayList(List<RespValue> values) {
        return CommandRegistry.toByteArrayList(values);
    }

    public static byte[] toBytes(RespValue value) {
        return CommandRegistry.toBytes(value);
    }

    public static String toUtf8String(RespValue value) {
        return CommandRegistry.toUtf8String(value);
    }
}

