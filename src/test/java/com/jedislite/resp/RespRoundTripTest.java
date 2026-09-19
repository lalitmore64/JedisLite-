package com.jedislite.resp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RespRoundTripTest {

    private RespValue roundTrip(RespValue original) {
        byte[] serialized = RespWriter.serialize(original);
        return RespReader.decode(serialized);
    }

    @Test
    @DisplayName("Round trips Simple Strings")
    void roundTripSimpleStrings() {
        assertThat(roundTrip(RespValue.OK)).isEqualTo(RespValue.OK);
        assertThat(roundTrip(RespValue.PONG)).isEqualTo(RespValue.PONG);
        assertThat(roundTrip(RespValue.simpleString("hello world"))).isEqualTo(RespValue.simpleString("hello world"));
    }

    @Test
    @DisplayName("Round trips Errors")
    void roundTripErrors() {
        assertThat(roundTrip(RespValue.err("syntax error"))).isEqualTo(RespValue.err("syntax error"));
        assertThat(roundTrip(RespValue.error("WRONGTYPE Operation against key"))).isEqualTo(RespValue.error("WRONGTYPE Operation against key"));
    }

    @Test
    @DisplayName("Round trips Integers")
    void roundTripIntegers() {
        assertThat(roundTrip(RespValue.ZERO)).isEqualTo(RespValue.ZERO);
        assertThat(roundTrip(RespValue.integer(42))).isEqualTo(RespValue.integer(42));
        assertThat(roundTrip(RespValue.integer(-99999))).isEqualTo(RespValue.integer(-99999));
        assertThat(roundTrip(RespValue.integer(Long.MAX_VALUE))).isEqualTo(RespValue.integer(Long.MAX_VALUE));
        assertThat(roundTrip(RespValue.integer(Long.MIN_VALUE))).isEqualTo(RespValue.integer(Long.MIN_VALUE));
    }

    @Test
    @DisplayName("Round trips Bulk Strings including null and empty")
    void roundTripBulkStrings() {
        assertThat(roundTrip(RespValue.nullBulkString())).isEqualTo(RespValue.nullBulkString());
        assertThat(roundTrip(RespValue.EMPTY_BULK_STRING)).isEqualTo(RespValue.EMPTY_BULK_STRING);
        assertThat(roundTrip(RespValue.bulkString("standard bulk string"))).isEqualTo(RespValue.bulkString("standard bulk string"));
    }

    @Test
    @DisplayName("Round trips Arbitrary Large Binary Payload")
    void roundTripBinaryPayload() {
        byte[] randomBytes = new byte[64 * 1024];
        new Random(42).nextBytes(randomBytes);

        RespValue.BulkString original = RespValue.bulkString(randomBytes);
        RespValue recovered = roundTrip(original);

        assertThat(recovered).isEqualTo(original);
    }

    @Test
    @DisplayName("Round trips Arrays including null, empty, and nested")
    void roundTripArrays() {
        assertThat(roundTrip(RespValue.nullArray())).isEqualTo(RespValue.nullArray());
        assertThat(roundTrip(RespValue.EMPTY_ARRAY)).isEqualTo(RespValue.EMPTY_ARRAY);

        RespValue complex = RespValue.array(
                RespValue.bulkString("MSET"),
                RespValue.bulkString("k1"),
                RespValue.bulkString("v1"),
                RespValue.bulkString("k2"),
                RespValue.bulkString("v2"),
                RespValue.nullBulkString(),
                RespValue.array(RespValue.integer(10), RespValue.integer(20)),
                RespValue.OK,
                RespValue.err("test")
        );

        assertThat(roundTrip(complex)).isEqualTo(complex);
    }

    @Test
    @DisplayName("Round trips Pipelined Command Batches (redis-cli simulation)")
    void roundTripPipelinedBatch() throws IOException {
        List<RespValue> commands = List.of(
                RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("user:1000"), RespValue.bulkString("Alice")),
                RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("user:1000")),
                RespValue.array(RespValue.bulkString("INCR"), RespValue.bulkString("counter")),
                RespValue.array(RespValue.bulkString("LPUSH"), RespValue.bulkString("mylist"), RespValue.bulkString("a"), RespValue.bulkString("b")),
                RespValue.array(RespValue.bulkString("LRANGE"), RespValue.bulkString("mylist"), RespValue.bulkString("0"), RespValue.bulkString("-1")),
                RespValue.array(RespValue.bulkString("PING"))
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (RespWriter writer = new RespWriter(baos)) {
            for (RespValue cmd : commands) {
                writer.write(cmd);
            }
            writer.flush();
        }

        byte[] wireBytes = baos.toByteArray();

        List<RespValue> deserialized = new ArrayList<>();
        try (RespReader reader = new RespReader(new ByteArrayInputStream(wireBytes))) {
            RespValue val;
            while ((val = reader.read()) != null) {
                deserialized.add(val);
            }
        }

        assertThat(deserialized).isEqualTo(commands);
    }
}

