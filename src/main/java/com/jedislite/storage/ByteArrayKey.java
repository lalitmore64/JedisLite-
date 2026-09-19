package com.jedislite.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class ByteArrayKey implements Comparable<ByteArrayKey> {

    private final byte[] bytes;
    private final int hashCode;

    public ByteArrayKey(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        this.bytes = bytes.clone();
        this.hashCode = Arrays.hashCode(this.bytes);
    }

    public byte[] toBytes() {
        return bytes.clone();
    }

    public String toUtf8String() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int length() {
        return bytes.length;
    }

    public static ByteArrayKey of(byte[] bytes) {
        return new ByteArrayKey(bytes);
    }

    public static ByteArrayKey of(String utf8String) {
        Objects.requireNonNull(utf8String, "utf8String cannot be null");
        return new ByteArrayKey(utf8String.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByteArrayKey other)) return false;
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(ByteArrayKey o) {
        return Arrays.compare(this.bytes, o.bytes);
    }

    @Override
    public String toString() {
        return toUtf8String();
    }
}

