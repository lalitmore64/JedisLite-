package com.jedislite.resp;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RespReader implements Closeable {

    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final int MAX_LINE_LENGTH = 64 * 1024;
    public static final int MAX_BULK_STRING_LENGTH = 512 * 1024 * 1024;
    public static final int MAX_ARRAY_LENGTH = 1024 * 1024;
    public static final int MAX_ARRAY_DEPTH = 64;

    private final InputStream in;

    public RespReader(InputStream in) {
        Objects.requireNonNull(in, "InputStream cannot be null");
        this.in = in instanceof BufferedInputStream bis ? bis : new BufferedInputStream(in, DEFAULT_BUFFER_SIZE);
    }

    public RespValue read() throws IOException {
        return readInternal(0);
    }

    private RespValue readInternal(int depth) throws IOException {
        int prefix = in.read();
        if (prefix == -1) {
            return null;
        }

        return switch ((byte) prefix) {
            case RespValue.SIMPLE_STRING_PREFIX -> readSimpleString();
            case RespValue.ERROR_PREFIX -> readError();
            case RespValue.INTEGER_PREFIX -> readInteger();
            case RespValue.BULK_STRING_PREFIX -> readBulkString();
            case RespValue.ARRAY_PREFIX -> readArray(depth);
            default -> throw new RespProtocolException("Unexpected RESP prefix byte: '" + (char) prefix + "' (0x" + Integer.toHexString(prefix) + ")");
        };
    }

    private RespValue.SimpleString readSimpleString() throws IOException {
        byte[] lineBytes = readLineBytes("SimpleString");
        String value = new String(lineBytes, StandardCharsets.UTF_8);
        return new RespValue.SimpleString(value);
    }

    private RespValue.Error readError() throws IOException {
        byte[] lineBytes = readLineBytes("Error");
        String message = new String(lineBytes, StandardCharsets.UTF_8);
        return new RespValue.Error(message);
    }

    private RespValue.Integer readInteger() throws IOException {
        byte[] lineBytes = readLineBytes("Integer");
        long value = parseLong(lineBytes);
        return new RespValue.Integer(value);
    }

    private RespValue.BulkString readBulkString() throws IOException {
        byte[] lengthBytes = readLineBytes("BulkString length");
        long length = parseLong(lengthBytes);

        if (length == -1) {
            return RespValue.NULL_BULK_STRING;
        }
        if (length < -1) {
            throw new RespProtocolException("Invalid bulk string length: " + length);
        }
        if (length > MAX_BULK_STRING_LENGTH) {
            throw new RespProtocolException("Bulk string length exceeds maximum (" + MAX_BULK_STRING_LENGTH + "): " + length);
        }

        int intLen = (int) length;
        byte[] data = in.readNBytes(intLen);
        if (data.length != intLen) {
            throw new RespProtocolException("Unexpected EOF reading bulk string data: expected " + intLen + " bytes, read " + data.length);
        }

        int cr = in.read();
        int lf = in.read();
        if (cr != RespValue.CR || lf != RespValue.LF) {
            throw new RespProtocolException("Bulk string data not terminated by CRLF");
        }

        return new RespValue.BulkString(data);
    }

    private RespValue.Array readArray(int depth) throws IOException {
        if (depth > MAX_ARRAY_DEPTH) {
            throw new RespProtocolException("Exceeded maximum array recursion depth (" + MAX_ARRAY_DEPTH + ")");
        }

        byte[] lengthBytes = readLineBytes("Array length");
        long count = parseLong(lengthBytes);

        if (count == -1) {
            return RespValue.NULL_ARRAY;
        }
        if (count < -1) {
            throw new RespProtocolException("Invalid array length: " + count);
        }
        if (count > MAX_ARRAY_LENGTH) {
            throw new RespProtocolException("Array length exceeds maximum (" + MAX_ARRAY_LENGTH + "): " + count);
        }
        if (count == 0) {
            return RespValue.EMPTY_ARRAY;
        }

        int intCount = (int) count;
        List<RespValue> elements = new ArrayList<>(intCount);
        for (int i = 0; i < intCount; i++) {
            RespValue element = readInternal(depth + 1);
            if (element == null) {
                throw new RespProtocolException("Unexpected EOF while reading array element at index " + i + " of " + intCount);
            }
            elements.add(element);
        }

        return new RespValue.Array(elements);
    }

    private byte[] readLineBytes(String context) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
        int b;
        while ((b = in.read()) != -1) {
            if (b == RespValue.CR) {
                int next = in.read();
                if (next == -1) {
                    throw new RespProtocolException("Unexpected EOF after CR in " + context);
                }
                if (next == RespValue.LF) {
                    return baos.toByteArray();
                }
                throw new RespProtocolException("Expected LF (0x0A) after CR in " + context + ", got: 0x" + Integer.toHexString(next));
            }
            baos.write(b);
            if (baos.size() > MAX_LINE_LENGTH) {
                throw new RespProtocolException("Line length exceeded maximum (" + MAX_LINE_LENGTH + " bytes) in " + context);
            }
        }
        throw new RespProtocolException("Unexpected EOF while reading line in " + context);
    }

    private long parseLong(byte[] asciiBytes) {
        if (asciiBytes == null || asciiBytes.length == 0) {
            throw new RespProtocolException("Empty integer/length field in RESP stream");
        }
        String s = new String(asciiBytes, StandardCharsets.US_ASCII);
        try {
            if (s.startsWith("+")) {
                return Long.parseLong(s.substring(1));
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new RespProtocolException("Malformed integer in RESP stream: '" + s + "'", e);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public static RespValue decode(byte[] bytes) {
        try (RespReader reader = new RespReader(new ByteArrayInputStream(bytes))) {
            return reader.read();
        } catch (IOException e) {
            throw new RespProtocolException("Failed to decode RESP value: " + e.getMessage(), e);
        }
    }

    public static List<RespValue> decodeAll(byte[] bytes) {
        List<RespValue> list = new ArrayList<>();
        try (RespReader reader = new RespReader(new ByteArrayInputStream(bytes))) {
            RespValue value;
            while ((value = reader.read()) != null) {
                list.add(value);
            }
            return list;
        } catch (IOException e) {
            throw new RespProtocolException("Failed to decode all RESP values: " + e.getMessage(), e);
        }
    }
}

