package com.jedislite.persistence;

import java.nio.charset.StandardCharsets;

public final class RdbConstants {

    private RdbConstants() {
    }

    public static final byte[] MAGIC = "JEDIS1".getBytes(StandardCharsets.US_ASCII);
    public static final int VERSION = 1;

    public static final byte OP_EXPIRE_TIME_MS = (byte) 0xFC;
    public static final byte OP_EOF = (byte) 0xFF;

    public static final byte TYPE_STRING = 0x00;
    public static final byte TYPE_HASH = 0x01;
    public static final byte TYPE_LIST = 0x02;
    public static final byte TYPE_SET = 0x03;
}

