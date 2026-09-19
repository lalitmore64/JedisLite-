package com.jedislite.storage;

public enum RedisDataType {
    STRING("string"),
    LIST("list"),
    HASH("hash"),
    SET("set"),
    NONE("none");

    private final String typeName;

    RedisDataType(String typeName) {
        this.typeName = typeName;
    }

    public String typeName() {
        return typeName;
    }
}

