package com.jedislite.engine;

import com.jedislite.engine.command.CommandRegistry;
import com.jedislite.resp.RespValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRegistryTest {

    private Database db;
    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        db = new Database();
        registry = new CommandRegistry();
    }

    private RespValue execute(String cmdName, RespValue... args) {
        return registry.execute(cmdName, List.of(args), db);
    }

    @Test
    @DisplayName("Unknown command returns ERR unknown command")
    void testUnknownCommand() {
        RespValue res = execute("FOOBAR");
        assertThat(res).isInstanceOf(RespValue.Error.class);
        assertThat(((RespValue.Error) res).message()).isEqualTo("ERR unknown command 'FOOBAR'");
    }

    @Test
    @DisplayName("Centralized arity checks return standard Redis error format")
    void testArityErrors() {

        RespValue errGet0 = execute("GET");
        assertThat(((RespValue.Error) errGet0).message()).isEqualTo("ERR wrong number of arguments for 'get' command");

        RespValue errGet2 = execute("GET", RespValue.bulkString("k1"), RespValue.bulkString("k2"));
        assertThat(((RespValue.Error) errGet2).message()).isEqualTo("ERR wrong number of arguments for 'get' command");

        RespValue errSet1 = execute("SET", RespValue.bulkString("k1"));
        assertThat(((RespValue.Error) errSet1).message()).isEqualTo("ERR wrong number of arguments for 'set' command");

        RespValue errExpire1 = execute("EXPIRE", RespValue.bulkString("k1"));
        assertThat(((RespValue.Error) errExpire1).message()).isEqualTo("ERR wrong number of arguments for 'expire' command");

        RespValue errTtl0 = execute("TTL");
        assertThat(((RespValue.Error) errTtl0).message()).isEqualTo("ERR wrong number of arguments for 'ttl' command");
    }

    @Test
    @DisplayName("Error translation for WRONGTYPE, NumberFormatException, and ArithmeticException")
    void testErrorTranslations() {

        execute("SET", RespValue.bulkString("mykey"), RespValue.bulkString("not_a_number"));

        RespValue wrongType = execute("LPUSH", RespValue.bulkString("mykey"), RespValue.bulkString("item"));
        assertThat(((RespValue.Error) wrongType).message())
                .isEqualTo("WRONGTYPE Operation against a key holding the wrong kind of value");

        RespValue nonInt = execute("INCR", RespValue.bulkString("mykey"));
        assertThat(((RespValue.Error) nonInt).message())
                .isEqualTo("ERR value is not an integer or out of range");

        execute("SET", RespValue.bulkString("num"), RespValue.bulkString(Long.toString(Long.MAX_VALUE)));
        RespValue overflow = execute("INCR", RespValue.bulkString("num"));
        assertThat(((RespValue.Error) overflow).message())
                .isEqualTo("ERR increment or decrement would overflow");
    }

    @Test
    @DisplayName("SET with EX and NX options")
    void testSetOptions() {

        RespValue res1 = execute("SET", RespValue.bulkString("k1"), RespValue.bulkString("v1"), RespValue.bulkString("EX"), RespValue.bulkString("10"));
        assertThat(res1).isEqualTo(RespValue.OK);
        RespValue ttl = execute("TTL", RespValue.bulkString("k1"));
        assertThat(((RespValue.Integer) ttl).value()).isGreaterThan(0);

        RespValue resNx = execute("SET", RespValue.bulkString("k1"), RespValue.bulkString("newV"), RespValue.bulkString("NX"));
        assertThat(resNx).isEqualTo(RespValue.nullBulkString());

        RespValue resNxNew = execute("SET", RespValue.bulkString("k2"), RespValue.bulkString("v2"), RespValue.bulkString("NX"));
        assertThat(resNxNew).isEqualTo(RespValue.OK);
    }
}

