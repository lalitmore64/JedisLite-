package com.jedislite.benchmark;

import com.jedislite.server.NioRedisServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkRunnerTest {

    private static NioRedisServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws IOException {
        server = new NioRedisServer(0);
        server.start();
        port = server.getPort();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Run load benchmark across increasing concurrency tiers (1, 10, 50, 100 clients)")
    void testLoadBenchmarkAcrossConcurrencyTiers() throws Exception {
        int[] clientTiers = {1, 10, 50, 100};
        String[] commands = {"PING", "SET", "GET"};
        int requestsPerClient = 500;

        List<JedisLiteBenchmark.BenchmarkResult> results = new ArrayList<>();

        System.out.println("\n==========================================================================================");
        System.out.println("  JedisLite Concurrency & Load Benchmark Report (NIO Single-Threaded Event Loop)");
        System.out.println("==========================================================================================");
        System.out.println(JedisLiteBenchmark.BenchmarkResult.formatHeader());

        for (String cmd : commands) {
            for (int clients : clientTiers) {
                JedisLiteBenchmark.BenchmarkResult res = JedisLiteBenchmark.runBenchmark(
                        "127.0.0.1", port, cmd, clients, requestsPerClient
                );
                results.add(res);
                System.out.println(res.formatRow());

                assertThat(res.requestsPerSecond()).isGreaterThan(0.0);
                assertThat(res.totalRequests()).isEqualTo(clients * requestsPerClient);
            }
            System.out.println(JedisLiteBenchmark.BenchmarkResult.formatFooter());
        }

        System.out.println("==========================================================================================\n");
    }
}

