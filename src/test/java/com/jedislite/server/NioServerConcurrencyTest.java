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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NioServerConcurrencyTest {

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

    private Socket createSocket() throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(5000);
        return socket;
    }

    @Test
    @DisplayName("concurrent client sockets execute commands")
    void testConcurrentSocketClientsHammeringServer() throws Exception {
        int clientCount = 25;
        int opsPerClient = 100;

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch readyLatch = new CountDownLatch(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successfulOps = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            tasks.add(() -> {
                try (Socket socket = createSocket()) {
                    RespWriter writer = new RespWriter(socket.getOutputStream());
                    RespReader reader = new RespReader(socket.getInputStream());

                    readyLatch.countDown();
                    startLatch.await();

                    for (int j = 0; j < opsPerClient; j++) {

                        writer.write(RespValue.array(RespValue.bulkString("PING")));
                        writer.flush();
                        RespValue pingRes = reader.read();
                        assertThat(pingRes).isEqualTo(RespValue.PONG);

                        String key = "client:" + clientId + ":" + j;
                        String val = "v:" + clientId + ":" + j;
                        writer.write(RespValue.array(
                                RespValue.bulkString("SET"),
                                RespValue.bulkString(key),
                                RespValue.bulkString(val)
                        ));
                        writer.flush();
                        RespValue setRes = reader.read();
                        assertThat(setRes).isEqualTo(RespValue.OK);

                        writer.write(RespValue.array(
                                RespValue.bulkString("GET"),
                                RespValue.bulkString(key)
                        ));
                        writer.flush();
                        RespValue getRes = reader.read();
                        assertThat(getRes).isEqualTo(RespValue.bulkString(val));

                        writer.write(RespValue.array(
                                RespValue.bulkString("INCR"),
                                RespValue.bulkString("global:counter")
                        ));
                        writer.flush();
                        RespValue incrRes = reader.read();
                        assertThat(incrRes).isInstanceOf(RespValue.Integer.class);

                        successfulOps.incrementAndGet();
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        boolean allReady = readyLatch.await(10, TimeUnit.SECONDS);
        assertThat(allReady).isTrue();

        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }

        assertThat(successfulOps.get()).isEqualTo(clientCount * opsPerClient);

        try (Socket verifySocket = createSocket()) {
            RespWriter writer = new RespWriter(verifySocket.getOutputStream());
            RespReader reader = new RespReader(verifySocket.getInputStream());

            writer.write(RespValue.array(
                    RespValue.bulkString("GET"),
                    RespValue.bulkString("global:counter")
            ));
            writer.flush();
            RespValue counterVal = reader.read();
            assertThat(counterVal).isEqualTo(RespValue.bulkString(String.valueOf(clientCount * opsPerClient)));
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("concurrent list pushes from multiple sockets")
    void testConcurrentListPushFromMultipleSockets() throws Exception {
        int clientCount = 20;
        int itemsPerClient = 50;
        String listKey = "concurrent:socket:list";

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch readyLatch = new CountDownLatch(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            tasks.add(() -> {
                try (Socket socket = createSocket()) {
                    RespWriter writer = new RespWriter(socket.getOutputStream());
                    RespReader reader = new RespReader(socket.getInputStream());

                    readyLatch.countDown();
                    startLatch.await();

                    for (int j = 0; j < itemsPerClient; j++) {
                        writer.write(RespValue.array(
                                RespValue.bulkString("LPUSH"),
                                RespValue.bulkString(listKey),
                                RespValue.bulkString("item:" + clientId + ":" + j)
                        ));
                        writer.flush();
                        RespValue res = reader.read();
                        assertThat(res).isInstanceOf(RespValue.Integer.class);
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }

        try (Socket verifySocket = createSocket()) {
            RespWriter writer = new RespWriter(verifySocket.getOutputStream());
            RespReader reader = new RespReader(verifySocket.getInputStream());

            writer.write(RespValue.array(
                    RespValue.bulkString("LLEN"),
                    RespValue.bulkString(listKey)
            ));
            writer.flush();
            RespValue llenRes = reader.read();
            assertThat(llenRes).isEqualTo(RespValue.integer(clientCount * itemsPerClient));
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("connection churn with rapid connect and disconnect")
    void testHighConnectionChurnWithConcurrentConnectAndDisconnect() throws Exception {
        int threadCount = 15;
        int connectionsPerThread = 20;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            tasks.add(() -> {
                startLatch.await();
                for (int conn = 0; conn < connectionsPerThread; conn++) {
                    try (Socket socket = createSocket()) {
                        RespWriter writer = new RespWriter(socket.getOutputStream());
                        RespReader reader = new RespReader(socket.getInputStream());

                        writer.write(RespValue.array(
                                RespValue.bulkString("SET"),
                                RespValue.bulkString("churn:" + threadId + ":" + conn),
                                RespValue.bulkString("val")
                        ));
                        writer.flush();
                        assertThat(reader.read()).isEqualTo(RespValue.OK);
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }

        executor.shutdown();
    }
}

