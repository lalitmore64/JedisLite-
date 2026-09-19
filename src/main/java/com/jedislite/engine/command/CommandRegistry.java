package com.jedislite.engine.command;

import com.jedislite.engine.Command;
import com.jedislite.engine.Database;
import com.jedislite.engine.command.impl.HashCommands;
import com.jedislite.engine.command.impl.KeyCommands;
import com.jedislite.engine.command.impl.ListCommands;
import com.jedislite.engine.command.impl.ServerCommands;
import com.jedislite.engine.command.impl.SetCommands;
import com.jedislite.engine.command.impl.StringCommands;
import com.jedislite.resp.RespValue;
import com.jedislite.storage.ByteArrayKey;
import com.jedislite.storage.WrongTypeException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class CommandRegistry {

    private final Map<String, CommandDefinition> registry = new HashMap<>();

    public CommandRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        StringCommands.register(this);
        KeyCommands.register(this);
        HashCommands.register(this);
        ListCommands.register(this);
        SetCommands.register(this);
        ServerCommands.register(this);
    }

    public void register(String name, int minArgs, int maxArgs, Command handler) {
        String upperName = name.toUpperCase(Locale.ROOT);
        registry.put(upperName, new CommandDefinition(upperName, minArgs, maxArgs, handler));
    }

    public CommandDefinition get(String name) {
        return registry.get(name.toUpperCase(Locale.ROOT));
    }

    public boolean contains(String name) {
        return registry.containsKey(name.toUpperCase(Locale.ROOT));
    }

    public RespValue execute(String commandName, List<RespValue> args, Database db) {
        String upper = commandName.toUpperCase(Locale.ROOT);
        CommandDefinition def = registry.get(upper);
        if (def == null) {
            return RespValue.err("unknown command '" + commandName + "'");
        }

        if (!def.isArityValid(args.size())) {
            return RespValue.err("wrong number of arguments for '" + commandName.toLowerCase(Locale.ROOT) + "' command");
        }

        try {
            return def.handler().execute(args, db);
        } catch (WrongTypeException e) {
            return RespValue.error(e.getMessage());
        } catch (NumberFormatException e) {
            return RespValue.err("value is not an integer or out of range");
        } catch (ArithmeticException e) {
            return RespValue.err("increment or decrement would overflow");
        } catch (Exception e) {
            return RespValue.err("Internal error executing '" + commandName + "': " + e.getMessage());
        }
    }

    public static ByteArrayKey toKey(RespValue value) {
        return ByteArrayKey.of(toBytes(value));
    }

    public static List<ByteArrayKey> toKeyList(List<RespValue> values) {
        List<ByteArrayKey> list = new ArrayList<>(values.size());
        for (RespValue val : values) {
            list.add(toKey(val));
        }
        return list;
    }

    public static List<byte[]> toByteArrayList(List<RespValue> values) {
        List<byte[]> list = new ArrayList<>(values.size());
        for (RespValue val : values) {
            list.add(toBytes(val));
        }
        return list;
    }

    public static byte[] toBytes(RespValue value) {
        return switch (value) {
            case RespValue.BulkString b -> b.data();
            case RespValue.SimpleString s -> s.value().getBytes(StandardCharsets.UTF_8);
            case RespValue.Integer i -> Long.toString(i.value()).getBytes(StandardCharsets.US_ASCII);
            case RespValue.Error e -> e.message().getBytes(StandardCharsets.UTF_8);
            case RespValue.Array a -> throw new IllegalArgumentException("Cannot convert Array to bytes");
        };
    }

    public static String toUtf8String(RespValue value) {
        return switch (value) {
            case RespValue.BulkString b -> b.asUtf8String();
            case RespValue.SimpleString s -> s.value();
            case RespValue.Integer i -> Long.toString(i.value());
            case RespValue.Error e -> e.message();
            case RespValue.Array a -> null;
        };
    }
}

