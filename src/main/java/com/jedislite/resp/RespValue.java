package com.jedislite.resp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public sealed interface RespValue
        permits RespValue.SimpleString,
                RespValue.Error,
                RespValue.Integer,
                RespValue.BulkString,
                RespValue.Array {

    byte SIMPLE_STRING_PREFIX = '+';
    byte ERROR_PREFIX = '-';
    byte INTEGER_PREFIX = ':';
    byte BULK_STRING_PREFIX = '$';
    byte ARRAY_PREFIX = '*';

    byte CR = '\r';
    byte LF = '\n';
    byte[] CRLF = new byte[]{CR, LF};

    SimpleString OK = new SimpleString("OK");
    SimpleString PONG = new SimpleString("PONG");
    Integer ZERO = new Integer(0);
    Integer ONE = new Integer(1);
    BulkString NULL_BULK_STRING = new BulkString(null);
    BulkString EMPTY_BULK_STRING = new BulkString(new byte[0]);
    Array NULL_ARRAY = new Array(null);
    Array EMPTY_ARRAY = new Array(Collections.emptyList());

    record SimpleString(String value) implements RespValue {
        public SimpleString {
            Objects.requireNonNull(value, "SimpleString value cannot be null");
            if (value.indexOf('\r') != -1 || value.indexOf('\n') != -1) {
                throw new IllegalArgumentException("SimpleString cannot contain CR or LF characters");
            }
        }
    }

    record Error(String message) implements RespValue {
        public Error {
            Objects.requireNonNull(message, "Error message cannot be null");
            if (message.indexOf('\r') != -1 || message.indexOf('\n') != -1) {
                throw new IllegalArgumentException("Error message cannot contain CR or LF characters");
            }
        }

        public String prefix() {
            int spaceIdx = message.indexOf(' ');
            return spaceIdx > 0 ? message.substring(0, spaceIdx) : message;
        }

        public String detail() {
            int spaceIdx = message.indexOf(' ');
            return spaceIdx > 0 ? message.substring(spaceIdx + 1) : "";
        }
    }

    record Integer(long value) implements RespValue {
    }

    record BulkString(byte[] data) implements RespValue {
        public BulkString(byte[] data) {
            this.data = data != null ? data.clone() : null;
        }

        @Override
        public byte[] data() {
            return data != null ? data.clone() : null;
        }

        public boolean isNull() {
            return data == null;
        }

        public int length() {
            return data == null ? -1 : data.length;
        }

        public String asUtf8String() {
            return data == null ? null : new String(data, StandardCharsets.UTF_8);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BulkString that)) return false;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            if (data == null) {
                return "BulkString[null]";
            }
            return "BulkString[" + asUtf8String() + " (" + data.length + " bytes)]";
        }
    }

    record Array(List<RespValue> elements) implements RespValue {
        public Array(List<RespValue> elements) {
            this.elements = elements != null ? List.copyOf(elements) : null;
        }

        public boolean isNull() {
            return elements == null;
        }

        public int size() {
            return elements == null ? -1 : elements.size();
        }

        public RespValue get(int index) {
            if (elements == null) {
                throw new IndexOutOfBoundsException("Array is null");
            }
            return elements.get(index);
        }
    }

    static SimpleString simpleString(String value) {
        return new SimpleString(value);
    }

    static Error error(String message) {
        return new Error(message);
    }

    static Error err(String message) {
        return new Error("ERR " + message);
    }

    static Integer integer(long value) {
        return new Integer(value);
    }

    static BulkString bulkString(byte[] data) {
        return data == null ? NULL_BULK_STRING : new BulkString(data);
    }

    static BulkString bulkString(String utf8String) {
        return utf8String == null ? NULL_BULK_STRING : new BulkString(utf8String.getBytes(StandardCharsets.UTF_8));
    }

    static BulkString nullBulkString() {
        return NULL_BULK_STRING;
    }

    static Array array(List<RespValue> elements) {
        return elements == null ? NULL_ARRAY : new Array(elements);
    }

    static Array array(RespValue... elements) {
        return elements == null ? NULL_ARRAY : new Array(Arrays.asList(elements));
    }

    static Array nullArray() {
        return NULL_ARRAY;
    }
}

