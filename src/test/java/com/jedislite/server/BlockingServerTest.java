package com.jedislite.server;

import com.jedislite.resp.RespReader;
import com.jedislite.resp.RespValue;
import com.jedislite.resp.RespWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingServerTest {

    private BlockingRedisServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = new BlockingRedisServer(0);
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
    @DisplayName("PING returns PONG")
    void testPing() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("PING"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isEqualTo(RespValue.PONG);
    }

    @Test
    @DisplayName("PING with message echoes message as bulk string")
    void testPingWithMessage() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("PING"), RespValue.bulkString("Hello Redis"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isEqualTo(RespValue.bulkString("Hello Redis"));
    }

    @Test
    @DisplayName("ECHO returns input bulk string")
    void testEcho() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("ECHO"), RespValue.bulkString("foobar"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isEqualTo(RespValue.bulkString("foobar"));
    }

    @Test
    @DisplayName("SET and GET round-trip")
    void testSetAndGet() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("key1"), RespValue.bulkString("value1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("key1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.bulkString("value1"));

            writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("missingKey")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.nullBulkString());
        }
    }

    @Test
    @DisplayName("DEL removes keys and returns count")
    void testDel() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("d1"), RespValue.bulkString("v1")));
            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("d2"), RespValue.bulkString("v2")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(
                    RespValue.bulkString("DEL"),
                    RespValue.bulkString("d1"),
                    RespValue.bulkString("d2"),
                    RespValue.bulkString("d3")
            ));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(2));
        }
    }

    @Test
    @DisplayName("EXISTS counts existing keys")
    void testExists() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("e1"), RespValue.bulkString("v1")));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.OK);

            writer.write(RespValue.array(
                    RespValue.bulkString("EXISTS"),
                    RespValue.bulkString("e1"),
                    RespValue.bulkString("e2")
            ));
            writer.flush();
            assertThat(reader.read()).isEqualTo(RespValue.integer(1));
        }
    }

    @Test
    @DisplayName("Command names are case-insensitive")
    void testCaseInsensitivity() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("pInG"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isEqualTo(RespValue.PONG);
    }

    @Test
    @DisplayName("Unknown commands return ERR")
    void testUnknownCommand() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("UNKNOWNCMD"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isInstanceOf(RespValue.Error.class);
        assertThat(((RespValue.Error) res).message()).contains("unknown command 'UNKNOWNCMD'");
    }

    @Test
    @DisplayName("Wrong number of arguments returns ERR")
    void testWrongArity() throws IOException {
        RespValue cmd = RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("k1"), RespValue.bulkString("extra"));
        RespValue res = executeCommand(cmd);
        assertThat(res).isInstanceOf(RespValue.Error.class);
        assertThat(((RespValue.Error) res).message()).contains("wrong number of arguments for 'get' command");
    }

    @Test
    @DisplayName("Pipelined commands over single connection")
    void testPipelining() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            RespWriter writer = new RespWriter(socket.getOutputStream());
            RespReader reader = new RespReader(socket.getInputStream());

            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("p1"), RespValue.bulkString("v1")));
            writer.write(RespValue.array(RespValue.bulkString("SET"), RespValue.bulkString("p2"), RespValue.bulkString("v2")));
            writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("p1")));
            writer.write(RespValue.array(RespValue.bulkString("GET"), RespValue.bulkString("p2")));
            writer.flush();

            assertThat(reader.read()).isEqualTo(RespValue.OK);
            assertThat(reader.read()).isEqualTo(RespValue.OK);
            assertThat(reader.read()).isEqualTo(RespValue.bulkString("v1"));
            assertThat(reader.read()).isEqualTo(RespValue.bulkString("v2"));
        }
    }

    @Test
    @DisplayName("Concurrent client connections execute correctly")
    void testConcurrentClients() throws Exception {
        int clientCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            tasks.add(() -> {
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    socket.setTcpNoDelay(true);
                    RespWriter writer = new RespWriter(socket.getOutputStream());
                    RespReader reader = new RespReader(socket.getInputStream());

                    String key = "concurrent:" + id;
                    String val = "val:" + id;

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
}

