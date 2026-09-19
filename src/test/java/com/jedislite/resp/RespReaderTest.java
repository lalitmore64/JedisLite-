package com.jedislite.resp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RespReaderTest {

    private RespValue decode(String asciiOrUtf8) {
        return RespReader.decode(asciiOrUtf8.getBytes(StandardCharsets.UTF_8));
    }

    private RespValue decode(byte[] rawBytes) {
        return RespReader.decode(rawBytes);
    }

    @Nested
    @DisplayName("Simple Strings (+)")
    class SimpleStrings {

        @Test
        void parsesOk() {
            RespValue val = decode("+OK\r\n");
            assertThat(val).isInstanceOf(RespValue.SimpleString.class);
            assertThat(((RespValue.SimpleString) val).value()).isEqualTo("OK");
        }

        @Test
        void parsesPong() {
            RespValue val = decode("+PONG\r\n");
            assertThat(val).isEqualTo(RespValue.PONG);
        }

        @Test
        void parsesEmptySimpleString() {
            RespValue val = decode("+\r\n");
            assertThat(val).isEqualTo(new RespValue.SimpleString(""));
        }

        @Test
        void parsesStringWithSpaces() {
            RespValue val = decode("+Hello World\r\n");
            assertThat(val).isEqualTo(new RespValue.SimpleString("Hello World"));
        }
    }

    @Nested
    @DisplayName("Errors (-)")
    class Errors {

        @Test
        void parsesStandardError() {
            RespValue val = decode("-ERR unknown command 'foobar'\r\n");
            assertThat(val).isInstanceOf(RespValue.Error.class);
            RespValue.Error err = (RespValue.Error) val;
            assertThat(err.message()).isEqualTo("ERR unknown command 'foobar'");
            assertThat(err.prefix()).isEqualTo("ERR");
            assertThat(err.detail()).isEqualTo("unknown command 'foobar'");
        }

        @Test
        void parsesWrongTypeError() {
            RespValue val = decode("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n");
            RespValue.Error err = (RespValue.Error) val;
            assertThat(err.prefix()).isEqualTo("WRONGTYPE");
            assertThat(err.detail()).isEqualTo("Operation against a key holding the wrong kind of value");
        }

        @Test
        void parsesErrorWithoutSpace() {
            RespValue val = decode("-CUSTOMERROR\r\n");
            RespValue.Error err = (RespValue.Error) val;
            assertThat(err.message()).isEqualTo("CUSTOMERROR");
            assertThat(err.prefix()).isEqualTo("CUSTOMERROR");
            assertThat(err.detail()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Integers (:)")
    class Integers {

        @Test
        void parsesZero() {
            RespValue val = decode(":0\r\n");
            assertThat(val).isEqualTo(RespValue.ZERO);
        }

        @Test
        void parsesPositiveInteger() {
            RespValue val = decode(":1000\r\n");
            assertThat(val).isEqualTo(new RespValue.Integer(1000L));
        }

        @Test
        void parsesExplicitPositiveSign() {
            RespValue val = decode(":+42\r\n");
            assertThat(val).isEqualTo(new RespValue.Integer(42L));
        }

        @Test
        void parsesNegativeInteger() {
            RespValue val = decode(":-12345\r\n");
            assertThat(val).isEqualTo(new RespValue.Integer(-12345L));
        }

        @Test
        void parsesLongMinAndMax() {
            assertThat(decode(":" + Long.MAX_VALUE + "\r\n"))
                    .isEqualTo(new RespValue.Integer(Long.MAX_VALUE));
            assertThat(decode(":" + Long.MIN_VALUE + "\r\n"))
                    .isEqualTo(new RespValue.Integer(Long.MIN_VALUE));
        }
    }

    @Nested
    @DisplayName("Bulk Strings ($)")
    class BulkStrings {

        @Test
        void parsesStandardBulkString() {
            RespValue val = decode("$5\r\nhello\r\n");
            assertThat(val).isInstanceOf(RespValue.BulkString.class);
            RespValue.BulkString bs = (RespValue.BulkString) val;
            assertThat(bs.isNull()).isFalse();
            assertThat(bs.length()).isEqualTo(5);
            assertThat(bs.asUtf8String()).isEqualTo("hello");
        }

        @Test
        void parsesEmptyBulkString() {
            RespValue val = decode("$0\r\n\r\n");
            assertThat(val).isInstanceOf(RespValue.BulkString.class);
            RespValue.BulkString bs = (RespValue.BulkString) val;
            assertThat(bs.isNull()).isFalse();
            assertThat(bs.length()).isEqualTo(0);
            assertThat(bs.asUtf8String()).isEqualTo("");
        }

        @Test
        void parsesNullBulkString() {
            RespValue val = decode("$-1\r\n");
            assertThat(val).isInstanceOf(RespValue.BulkString.class);
            RespValue.BulkString bs = (RespValue.BulkString) val;
            assertThat(bs.isNull()).isTrue();
            assertThat(bs.length()).isEqualTo(-1);
            assertThat(bs.asUtf8String()).isNull();
            assertThat(bs).isEqualTo(RespValue.NULL_BULK_STRING);
        }

        @Test
        void parsesBinarySafeDataWithInteriorCrlfAndNullBytes() {
            byte[] binaryPayload = new byte[]{0x00, '\r', '\n', 0x1F, (byte) 0xFF, 'a', 'b', 'c'};
            byte[] header = ("$" + binaryPayload.length + "\r\n").getBytes(StandardCharsets.US_ASCII);
            byte[] trailer = "\r\n".getBytes(StandardCharsets.US_ASCII);

            byte[] fullWire = new byte[header.length + binaryPayload.length + trailer.length];
            System.arraycopy(header, 0, fullWire, 0, header.length);
            System.arraycopy(binaryPayload, 0, fullWire, header.length, binaryPayload.length);
            System.arraycopy(trailer, 0, fullWire, header.length + binaryPayload.length, trailer.length);

            RespValue val = decode(fullWire);
            assertThat(val).isInstanceOf(RespValue.BulkString.class);
            RespValue.BulkString bs = (RespValue.BulkString) val;
            assertThat(bs.data()).containsExactly(binaryPayload);
        }
    }

    @Nested
    @DisplayName("Arrays (*)")
    class Arrays {

        @Test
        void parsesEmptyArray() {
            RespValue val = decode("*0\r\n");
            assertThat(val).isInstanceOf(RespValue.Array.class);
            RespValue.Array arr = (RespValue.Array) val;
            assertThat(arr.isNull()).isFalse();
            assertThat(arr.size()).isEqualTo(0);
            assertThat(arr.elements()).isEmpty();
        }

        @Test
        void parsesNullArray() {
            RespValue val = decode("*-1\r\n");
            assertThat(val).isInstanceOf(RespValue.Array.class);
            RespValue.Array arr = (RespValue.Array) val;
            assertThat(arr.isNull()).isTrue();
            assertThat(arr.size()).isEqualTo(-1);
            assertThat(arr).isEqualTo(RespValue.NULL_ARRAY);
        }

        @Test
        void parsesStandardRedisCommand() {

            String redisCmd = "*3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n";
            RespValue val = decode(redisCmd);
            assertThat(val).isInstanceOf(RespValue.Array.class);
            RespValue.Array arr = (RespValue.Array) val;
            assertThat(arr.size()).isEqualTo(3);
            assertThat(((RespValue.BulkString) arr.get(0)).asUtf8String()).isEqualTo("SET");
            assertThat(((RespValue.BulkString) arr.get(1)).asUtf8String()).isEqualTo("mykey");
            assertThat(((RespValue.BulkString) arr.get(2)).asUtf8String()).isEqualTo("myvalue");
        }

        @Test
        void parsesMixedTypesInArray() {
            String input = "*5\r\n:1\r\n:2\r\n:3\r\n:4\r\n$5\r\nhello\r\n";
            RespValue val = decode(input);
            assertThat(val).isEqualTo(RespValue.array(
                    RespValue.integer(1),
                    RespValue.integer(2),
                    RespValue.integer(3),
                    RespValue.integer(4),
                    RespValue.bulkString("hello")
            ));
        }

        @Test
        void parsesArrayWithNullElements() {
            String input = "*3\r\n$3\r\nfoo\r\n$-1\r\n$3\r\nbar\r\n";
            RespValue val = decode(input);
            assertThat(val).isEqualTo(RespValue.array(
                    RespValue.bulkString("foo"),
                    RespValue.nullBulkString(),
                    RespValue.bulkString("bar")
            ));
        }

        @Test
        void parsesNestedArrays() {
            String input = "*2\r\n*3\r\n:1\r\n:2\r\n:3\r\n*2\r\n+Foo\r\n-Bar\r\n";
            RespValue val = decode(input);
            assertThat(val).isEqualTo(RespValue.array(
                    RespValue.array(RespValue.integer(1), RespValue.integer(2), RespValue.integer(3)),
                    RespValue.array(RespValue.simpleString("Foo"), RespValue.error("Bar"))
            ));
        }
    }

    @Nested
    @DisplayName("Pipelining & Stream Handling")
    class Pipelining {

        @Test
        void parsesMultiplePipelinedCommandsFromSingleStream() throws IOException {
            String stream = "*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n*1\r\n$4\r\nPING\r\n+OK\r\n";
            try (RespReader reader = new RespReader(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)))) {
                RespValue first = reader.read();
                assertThat(first).isEqualTo(RespValue.array(RespValue.bulkString("ECHO"), RespValue.bulkString("hello")));

                RespValue second = reader.read();
                assertThat(second).isEqualTo(RespValue.array(RespValue.bulkString("PING")));

                RespValue third = reader.read();
                assertThat(third).isEqualTo(RespValue.OK);

                RespValue fourth = reader.read();
                assertThat(fourth).isNull();
            }
        }

        @Test
        void decodeAllParsesEverything() {
            String stream = ":1\r\n:2\r\n:3\r\n";
            List<RespValue> values = RespReader.decodeAll(stream.getBytes(StandardCharsets.UTF_8));
            assertThat(values).containsExactly(
                    RespValue.integer(1),
                    RespValue.integer(2),
                    RespValue.integer(3)
            );
        }

        @Test
        void emptyStreamReturnsNullOnCleanEof() throws IOException {
            try (RespReader reader = new RespReader(new ByteArrayInputStream(new byte[0]))) {
                assertThat(reader.read()).isNull();
            }
        }
    }

    @Nested
    @DisplayName("Protocol Error Handling & Wire Safety")
    class ProtocolErrors {

        @ParameterizedTest
        @ValueSource(strings = {
                "?", "X", " ", "\n", "@", "~"
        })
        void rejectsInvalidPrefixByte(String prefix) {
            assertThatThrownBy(() -> decode(prefix + "test\r\n"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Unexpected RESP prefix byte");
        }

        @Test
        void rejectsTruncatedSimpleString() {
            assertThatThrownBy(() -> decode("+Incomplete"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Unexpected EOF");
        }

        @Test
        void rejectsLoneCarriageReturn() {
            assertThatThrownBy(() -> decode("+Test\rNotLF"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Expected LF");
        }

        @Test
        void rejectsMalformedInteger() {
            assertThatThrownBy(() -> decode(":not_a_number\r\n"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Malformed integer");
        }

        @Test
        void rejectsNegativeLengthOtherThanMinusOneForBulkString() {
            assertThatThrownBy(() -> decode("$-2\r\n"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Invalid bulk string length");
        }

        @Test
        void rejectsTruncatedBulkStringData() {
            assertThatThrownBy(() -> decode("$10\r\nshort\r\n"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Unexpected EOF reading bulk string data");
        }

        @Test
        void rejectsBulkStringMissingEndingCrlf() {
            assertThatThrownBy(() -> decode("$5\r\nhelloXX"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Bulk string data not terminated by CRLF");
        }

        @Test
        void rejectsNegativeLengthOtherThanMinusOneForArray() {
            assertThatThrownBy(() -> decode("*-5\r\n"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Invalid array length");
        }

        @Test
        void rejectsTruncatedArrayElements() {
            assertThatThrownBy(() -> decode("*3\r\n:1\r\n:2\r\n"))
                    .isInstanceOf(RespProtocolException.class)
                    .hasMessageContaining("Unexpected EOF while reading array element");
        }
    }
}

