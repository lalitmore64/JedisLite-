package com.jedislite.server;

import com.jedislite.resp.RespReader;
import com.jedislite.resp.RespValue;
import com.jedislite.resp.RespWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class NioServerTest {

    private NioRedisServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = new NioRedisServer(0);
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.stop();
        }
    }

    private RespValue executeCommand(RespValue command) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(command);
            writer.flush();

            return reader.read();
        }
    }

    @Test
    @DisplayName("NIO: PING returns PONG")
    void testPing() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("PING"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isEqualTo(RespValue.PONG);
    }

    @Test
    @DisplayName("NIO: PING with message echoes message")
    void testPingWithMessage() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("PING"), RespValue.bulkString("NIO works!"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isEqualTo(RespValue.bulkString("NIO works!"));
    }

    @Test
    @DisplayName("NIO: ECHO returns input bulk string")
    void testEcho() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("ECHO"), RespValue.bulkString("hello nio"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isEqualTo(RespValue.bulkString("hello nio"));
    }

    @Test
    @DisplayName("NIO: SET and GET round-trip")
    void testSetAndGet() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("k1"), RespValue.bulkString("v1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("k1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.bulkString("v1"));

            writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("nonexistent")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.nullBulkString());
        }
    }

    @Test
    @DisplayName("NIO: DEL and EXISTS operations")
    void testDelAndExists() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("x1"), RespValue.bulkString("val1")));
            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("x2"), RespValue.bulkString("val2")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(RespValue.bulkString("EXISTS"), RespValue.bulkString("x1"), RespValue.bulkString("x2"), RespValue.bulkString("x3")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(2));

            writer.write(RespValue.array(RespValue.bulkString("DEL"), RespValue.bulkString("x1"), RespValue.bulkString("x3")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));

            writer.write(RespValue.array(RespValue.bulkString("EXISTS"), RespValue.bulkString("x1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(0));
        }
    }

    @Test
    @DisplayName("NIO: Command name case-insensitivity and error handling")
    void testCaseInsensitiveAndErrors() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("pInG")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.PONG);

            writer.write(RespValue.array(RespValue.bulkString("BOGUS")));
            writer.flush();
            RespValue err1 = reader.read();
            assertThat(err1).isInstanceOf(RespValue.Error.class);
            assertThat(((RespValue.Error) err1).message()).contains("unknown command 'BOGUS'");

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("k1")));
            writer.flush();
            RespValue err2 = reader.read();
            assertThat(err2).isInstanceOf(RespValue.Error.class);
            assertThat(((RespValue.Error) err2).message()).contains("wrong number of arguments for 'set' command");
        }
    }

    @Test
    @DisplayName("NIO: Pipelined batch of commands")
    void testPipelining() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            int batchSize = 10;
            for (int i = 0; i < batchSize; i++) {
                writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("pipe:" + i), RespValue.bulkString("val:" + i)));
            }
            writer.flush();

            for (int i = 0; i < batchSize; i++) {
                assertThat(reader.read()).isEqualTo(RespValue.OK);
            }

            for (int i = 0; i < batchSize; i++) {
                writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("pipe:" + i)));
            }
            writer.flush();

            for (int i = 0; i < batchSize; i++) {
                assertThat(reader.read()).isEqualTo(RespValue.bulkString("val:" + i));
            }
        }
    }

    @Test
    @DisplayName("NIO: Fragmented / Partial TCP frames sent across multiple writes")
    void testFragmentedPacketDelivery() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            RespReader reader = new RespReader(socket.getInputStream());

            String rawCmd = "*3\r\n$3\r\nSET\r\n$7\r\nfragkey\r\n$7\r\nfragval\r\n";
            byte[] bytes = rawCmd.getBytes(StandardCharsets.UTF_8);

            int chunkSize = 3;
            for (int i = 0; i < bytes.length; i += chunkSize) {
                int len = Math.min(chunkSize, bytes.length - i);
                out.write(bytes, i, len);
                out.flush();
                Thread.sleep(5);
            }

            RespValue setResponse = reader.read();
            assertThat(setResponse).isEqualTo(RespValue.OK);

            RespWriter writer = new RespWriter(out);
            writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("fragkey")));
            writer.flush();

            RespValue getResponse = reader.read();
            assertThat(getResponse).isEqualTo(RespValue.bulkString("fragval"));
        }
    }

    @Test
    @DisplayName("NIO: 50 concurrent client connections on single-threaded event loop")
    void testHighConcurrencyOnEventLoop() throws Exception {
        int clientCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            tasks.add(() -> {
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    socket.setTcpNoDelay(true);
                    RespWriter writer = new RespWriter(socket.getOutputStream());
                    RespReader reader = new RespReader(socket.getInputStream());

                    String key = "eventloop:" + id;
                    String val = "v:" + id;

                    writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString(key), RespValue.bulkString(val)));
                    writer.flush();
                    assertThat(reader.read()).isEqualTo(RespValue.OK);

                    writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString(key)));
                    writer.flush();
                    assertThat(reader.read()).isEqualTo(RespValue.bulkString(val));
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();
    }

    @Test
    @DisplayName("NIO: Hash commands over TCP socket (HSET, HGET, HDEL, HEXISTS, HLEN, HGETALL)")
    void testHashCommandsOverTcp() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(
                    RespValue.bulkString("HSET"),
                    RespValue.bulkString("user:1"),
                    RespValue.bulkString("name"),
                    RespValue.bulkString("Bob"),
                    RespValue.bulkString("age"),
                    RespValue.bulkString("30")
            ));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(2));

            writer.write(RespValue.array(RespValue.bulkString("HGET"), RespValue.bulkString("user:1"), RespValue.bulkString("name")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.bulkString("Bob"));

            writer.write(RespValue.array(RespValue.bulkString("HLEN"), RespValue.bulkString("user:1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(2));

            writer.write(RespValue.array(RespValue.bulkString("HEXISTS"), RespValue.bulkString("user:1"), RespValue.bulkString("age")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));

            writer.write(RespValue.array(RespValue.bulkString("HDEL"), RespValue.bulkString("user:1"), RespValue.bulkString("age")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));

            writer.write(RespValue.array(RespValue.bulkString("HGETALL"), RespValue.bulkString("user:1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.array(RespValue.bulkString("name"), RespValue.bulkString("Bob")));
        }
    }

    @Test
    @DisplayName("NIO: List commands over TCP socket (LPUSH, RPUSH, LPOP, LRANGE, LLEN, LINDEX)")
    void testListCommandsOverTcp() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("RPUSH"), RespValue.bulkString("tasks"), RespValue.bulkString("task1"), RespValue.bulkString("task2")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(2));

            writer.write(RespValue.array(RespValue.bulkString("LPUSH"), RespValue.bulkString("tasks"), RespValue.bulkString("task0")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(3));

            writer.write(RespValue.array(RespValue.bulkString("LRANGE"), RespValue.bulkString("tasks"), RespValue.bulkString("0"), RespValue.bulkString("-1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.array(
                    RespValue.bulkString("task0"),
                    RespValue.bulkString("task1"),
                    RespValue.bulkString("task2")
            ));

            writer.write(RespValue.array(RespValue.bulkString("LINDEX"), RespValue.bulkString("tasks"), RespValue.bulkString("1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.bulkString("task1"));

            writer.write(RespValue.array(RespValue.bulkString("LPOP"), RespValue.bulkString("tasks")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.bulkString("task0"));

            writer.write(RespValue.array(RespValue.bulkString("LLEN"), RespValue.bulkString("tasks")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(2));
        }
    }

    @Test
    @DisplayName("NIO: Set commands over TCP socket (SADD, SISMEMBER, SMEMBERS, SCARD, SREM)")
    void testSetCommandsOverTcp() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(
                    RespValue.bulkString("SADD"),
                    RespValue.bulkString("fruits"),
                    RespValue.bulkString("apple"),
                    RespValue.bulkString("banana"),
                    RespValue.bulkString("orange")
            ));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(3));

            writer.write(RespValue.array(RespValue.bulkString("SCARD"), RespValue.bulkString("fruits")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(3));

            writer.write(RespValue.array(RespValue.bulkString("SISMEMBER"), RespValue.bulkString("fruits"), RespValue.bulkString("banana")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));

            writer.write(RespValue.array(RespValue.bulkString("SREM"), RespValue.bulkString("fruits"), RespValue.bulkString("banana")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));
        }
    }

    @Test
    @DisplayName("NIO: TYPE command and WRONGTYPE protocol error over TCP")
    void testTypeAndWrongTypeErrorOverTcp() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("strVal"), RespValue.bulkString("hello")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(RespValue.bulkString("TYPE"), RespValue.bulkString("strVal")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.simpleString("string"));

            writer.write(RespValue.array(RespValue.bulkString("LPUSH"), RespValue.bulkString("strVal"), RespValue.bulkString("item")));
            writer.flush();
            RespValue wrongTypeErr = reader.read();
            assertThat(wrongTypeErr).isInstanceOf(RespValue.Error.class);
            assertThat(((RespValue.Error) wrongTypeErr).message()).contains("WRONGTYPE");

            writer.write(RespValue.array(RespValue.bulkString("HSET"), RespValue.bulkString("strVal"), RespValue.bulkString("f"), RespValue.bulkString("v")));
            writer.flush();
            RespValue wrongTypeErr2 = reader.read();
            assertThat(wrongTypeErr2).isInstanceOf(RespValue.Error.class);
            assertThat(((RespValue.Error) wrongTypeErr2).message()).contains("WRONGTYPE");
        }
    }

    @Test
    @DisplayName("NIO: INCR, DECR, EXPIRE, TTL, and SET EX over TCP socket")
    void testIncrDecrAndTtlOverTcp() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("INCR"), RespValue.bulkString("visits")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));

            writer.write(RespValue.array(RespValue.bulkString("INCR"), RespValue.bulkString("visits")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(2));

            writer.write(RespValue.array(RespValue.bulkString("DECR"), RespValue.bulkString("visits")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));

            writer.write(RespValue.array(RespValue.bulkString("EXPIRE"), RespValue.bulkString("visits"), RespValue.bulkString("100")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));

            writer.write(RespValue.array(RespValue.bulkString("TTL"), RespValue.bulkString("visits")));
            writer.flush();
            RespValue ttlRes = reader.read();
            assertThat(ttlRes).isInstanceOf(RespValue.Integer.class);
            assertThat(((RespValue.Integer) ttlRes).value()).isGreaterThan(0);

            writer.write(RespValue.array(
                    RespValue.bulkString("SET"),
                    RespValue.bulkString("session"),
                    RespValue.bulkString("sess123"),
                    RespValue.bulkString("EX"),
                    RespValue.bulkString("300")
            ));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(RespValue.bulkString("TTL"), RespValue.bulkString("session")));
            writer.flush();
            RespValue sessionTtl = reader.read();
            assertThat(((RespValue.Integer) sessionTtl).value()).isBetween(290L, 300L);
        }
    }

    @Test
    @DisplayName("NIO: SAVE and BGSAVE commands over TCP socket")
    void testSaveAndBgsaveOverTcp() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("persistKey"), RespValue.bulkString("persistVal")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(RespValue.bulkString("SAVE")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(RespValue.bulkString("BGSAVE")));
            writer.flush();
            RespValue bgRes = reader.read();
            assertThat(bgRes).isInstanceOf(RespValue.SimpleString.class);
            assertThat(((RespValue.SimpleString) bgRes).value()).contains("Background saving started");
        }
    }
}

