package com.jedislite.resp;

import java.io.ByteArrayOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class RespWriter implements Flushable, AutoCloseable {

    private final OutputStream out;

    public RespWriter(OutputStream out) {
        this.out = Objects.requireNonNull(out, "OutputStream cannot be null");
    }

    public void write(RespValue value) throws IOException {
        Objects.requireNonNull(value, "RespValue cannot be null");
        switch (value) {
            case RespValue.SimpleString s -> writeSimpleString(s.value());
            case RespValue.Error e -> writeError(e.message());
            case RespValue.Integer i -> writeInteger(i.value());
            case RespValue.BulkString b -> {
                if (b.isNull()) {
                    writeNullBulkString();
                } else {
                    writeBulkString(b.data());
                }
            }
            case RespValue.Array a -> {
                if (a.isNull()) {
                    writeNullArray();
                } else {
                    writeArrayHeader(a.size());
                    for (RespValue element : a.elements()) {
                        write(element);
                    }
                }
            }
        }
    }

    public void writeSimpleString(String value) throws IOException {
        out.write(RespValue.SIMPLE_STRING_PREFIX);
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(RespValue.CRLF);
    }

    public void writeError(String message) throws IOException {
        out.write(RespValue.ERROR_PREFIX);
        out.write(message.getBytes(StandardCharsets.UTF_8));
        out.write(RespValue.CRLF);
    }

    public void writeInteger(long value) throws IOException {
        out.write(RespValue.INTEGER_PREFIX);
        writeAsciiLong(value);
        out.write(RespValue.CRLF);
    }

    public void writeBulkString(byte[] data) throws IOException {
        if (data == null) {
            writeNullBulkString();
            return;
        }
        out.write(RespValue.BULK_STRING_PREFIX);
        writeAsciiLong(data.length);
        out.write(RespValue.CRLF);
        if (data.length > 0) {
            out.write(data);
        }
        out.write(RespValue.CRLF);
    }

    public void writeBulkString(String utf8String) throws IOException {
        if (utf8String == null) {
            writeNullBulkString();
        } else {
            writeBulkString(utf8String.getBytes(StandardCharsets.UTF_8));
        }
    }

    public void writeNullBulkString() throws IOException {
        out.write(RespValue.BULK_STRING_PREFIX);
        out.write('-');
        out.write('1');
        out.write(RespValue.CRLF);
    }

    public void writeArrayHeader(int count) throws IOException {
        out.write(RespValue.ARRAY_PREFIX);
        writeAsciiLong(count);
        out.write(RespValue.CRLF);
    }

    public void writeNullArray() throws IOException {
        out.write(RespValue.ARRAY_PREFIX);
        out.write('-');
        out.write('1');
        out.write(RespValue.CRLF);
    }

    private void writeAsciiLong(long value) throws IOException {
        out.write(Long.toString(value).getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public static byte[] serialize(RespValue value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (RespWriter writer = new RespWriter(baos)) {
            writer.write(value);
            writer.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RespProtocolException("Failed to serialize RESP value: " + e.getMessage(), e);
        }
    }
}

