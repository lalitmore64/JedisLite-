package com.jedislite.resp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RespWriterTest {

    private String serializeToString(RespValue value) {
        byte[] bytes = RespWriter.serialize(value);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] serializeToBytes(RespValue value) {
        return RespWriter.serialize(value);
    }

    @Nested
    @DisplayName("Simple String Serialization")
    class SimpleStrings {

        @Test
        void serializesOk() {
            assertThat(serializeToString(RespValue.OK)).isEqualTo("+OK\r\n");
        }

        @Test
        void serializesPong() {
            assertThat(serializeToString(RespValue.PONG)).isEqualTo("+PONG\r\n");
        }

        @Test
        void serializesArbitrarySimpleString() {
            assertThat(serializeToString(RespValue.simpleString("HELLO WORLD"))).isEqualTo("+HELLO WORLD\r\n");
        }

        @Test
        void rejectsCarriageReturnOrNewlineInSimpleString() {
            assertThatThrownBy(() -> RespValue.simpleString("Hello\nWorld"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RespValue.simpleString("Hello\rWorld"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Error Serialization")
    class Errors {

        @Test
        void serializesStandardError() {
            assertThat(serializeToString(RespValue.err("unknown command 'foobar'")))
                    .isEqualTo("-ERR unknown command 'foobar'\r\n");
        }

        @Test
        void serializesCustomError() {
            assertThat(serializeToString(RespValue.error("WRONGTYPE Operation against key")))
                    .isEqualTo("-WRONGTYPE Operation against key\r\n");
        }

        @Test
        void rejectsCarriageReturnOrNewlineInError() {
            assertThatThrownBy(() -> RespValue.error("ERR with\nnewline"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RespValue.error("ERR with\rcarriage return"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Integer Serialization")
    class Integers {

        @Test
        void serializesZero() {
            assertThat(serializeToString(RespValue.ZERO)).isEqualTo(":0\r\n");
        }

        @Test
        void serializesPositiveInteger() {
            assertThat(serializeToString(RespValue.integer(1000))).isEqualTo(":1000\r\n");
        }

        @Test
        void serializesNegativeInteger() {
            assertThat(serializeToString(RespValue.integer(-42))).isEqualTo(":-42\r\n");
        }

        @Test
        void serializesBoundaryValues() {
            assertThat(serializeToString(RespValue.integer(Long.MAX_VALUE)))
                    .isEqualTo(":" + Long.MAX_VALUE + "\r\n");
            assertThat(serializeToString(RespValue.integer(Long.MIN_VALUE)))
                    .isEqualTo(":" + Long.MIN_VALUE + "\r\n");
        }
    }

    @Nested
    @DisplayName("Bulk String Serialization")
    class BulkStrings {

        @Test
        void serializesStandardBulkString() {
            assertThat(serializeToString(RespValue.bulkString("hello")))
                    .isEqualTo("$5\r\nhello\r\n");
        }

        @Test
        void serializesEmptyBulkString() {
            assertThat(serializeToString(RespValue.EMPTY_BULK_STRING))
                    .isEqualTo("$0\r\n\r\n");
        }

        @Test
        void serializesNullBulkString() {
            assertThat(serializeToString(RespValue.nullBulkString()))
                    .isEqualTo("$-1\r\n");
        }

        @Test
        void serializesBinaryDataSafely() {
            byte[] raw = new byte[]{0x00, '\r', '\n', (byte) 0xFF};
            byte[] serialized = serializeToBytes(RespValue.bulkString(raw));

            byte[] expectedHeader = "$4\r\n".getBytes(StandardCharsets.US_ASCII);
            byte[] expectedTrailer = "\r\n".getBytes(StandardCharsets.US_ASCII);

            byte[] expected = new byte[expectedHeader.length + raw.length + expectedTrailer.length];
            System.arraycopy(expectedHeader, 0, expected, 0, expectedHeader.length);
            System.arraycopy(raw, 0, expected, expectedHeader.length, raw.length);
            System.arraycopy(expectedTrailer, 0, expected, expectedHeader.length + raw.length, expectedTrailer.length);

            assertThat(serialized).containsExactly(expected);
        }
    }

    @Nested
    @DisplayName("Array Serialization")
    class Arrays {

        @Test
        void serializesEmptyArray() {
            assertThat(serializeToString(RespValue.EMPTY_ARRAY))
                    .isEqualTo("*0\r\n");
        }

        @Test
        void serializesNullArray() {
            assertThat(serializeToString(RespValue.nullArray()))
                    .isEqualTo("*-1\r\n");
        }

        @Test
        void serializesStandardCommandArray() {
            RespValue cmd = RespValue.array(
                    RespValue.bulkString("SET"),
                    RespValue.bulkString("mykey"),
                    RespValue.bulkString("myval")
            );
            assertThat(serializeToString(cmd))
                    .isEqualTo("*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$5\r\nmyval\r\n");
        }

        @Test
        void serializesMixedTypesAndNullElements() {
            RespValue mixed = RespValue.array(
                    RespValue.bulkString("GET"),
                    RespValue.nullBulkString(),
                    RespValue.integer(42),
                    RespValue.OK
            );
            assertThat(serializeToString(mixed))
                    .isEqualTo("*4\r\n$3\r\nGET\r\n$-1\r\n:42\r\n+OK\r\n");
        }

        @Test
        void serializesNestedArrays() {
            RespValue nested = RespValue.array(
                    RespValue.array(RespValue.integer(1), RespValue.integer(2)),
                    RespValue.array(RespValue.simpleString("A"))
            );
            assertThat(serializeToString(nested))
                    .isEqualTo("*2\r\n*2\r\n:1\r\n:2\r\n*1\r\n+A\r\n");
        }
    }

    @Test
    @DisplayName("Direct OutputStream streaming writes multiple values sequentially")
    void streamingWrites() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (RespWriter writer = new RespWriter(baos)) {
            writer.write(RespValue.OK);
            writer.write(RespValue.integer(100));
            writer.write(RespValue.bulkString("test"));
            writer.flush();
        }

        assertThat(baos.toString(StandardCharsets.UTF_8))
                .isEqualTo("+OK\r\n:100\r\n$4\r\ntest\r\n");
    }
}

