package com.jedislite.engine.command;

import com.jedislite.engine.Command;

import java.util.Objects;

public record CommandDefinition(String name, int minArgs, int maxArgs, Command handler) {

    public CommandDefinition {
        Objects.requireNonNull(name, "Command name cannot be null");
        Objects.requireNonNull(handler, "Command handler cannot be null");
    }

    public boolean isArityValid(int argCount) {
        if (argCount < minArgs) {
            return false;
        }
        return maxArgs == -1 || argCount <= maxArgs;
    }
}

