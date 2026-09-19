package com.jedislite.benchmark;

import com.jedislite.resp.RespReader;
import com.jedislite.resp.RespValue;
import com.jedislite.resp.RespWriter;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class JedisLiteBenchmark {

    public record BenchmarkResult(
            String command,
            int clients,
            int totalRequests,
            double durationSeconds,
            double requestsPerSecond,
            double avgLatencyMs,
            double p50LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double maxLatencyMs
    ) {
        public String formatRow() {
            return String.format(
                    "| %-8s | %7d | %8d | %8.2fs | %10.1f | %7.2fms | %7.2fms | %7.2fms | %7.2fms | %7.2fms |",
                    command, clients, totalRequests, durationSeconds, requestsPerSecond,
                    avgLatencyMs, p50LatencyMs, p95LatencyMs, p99LatencyMs, maxLatencyMs
            );
        }

        public static String formatHeader() {
            return """
+----------+---------+----------+-----------+------------+----------+----------+----------+----------+----------+
| Command  | Clients | Requests | Duration  |  Req/sec   | Avg Lat  | p50 Lat  | p95 Lat  | p99 Lat  | Max Lat  |
+----------+---------+----------+-----------+------------+----------+----------+----------+----------+----------+""";
        }

        public static String formatFooter() {
            return "+----------+---------+----------+-----------+------------+----------+----------+----------+----------+----------+";
        }
    }

    public static BenchmarkResult runBenchmark(
            String host,
            int port,
            String commandName,
            int concurrency,
            int requestsPerClient
    ) throws Exception {
        int totalRequests = concurrency * requestsPerClient;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch readyLatch = new CountDownLatch(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);

        long[][] threadLatencies = new long[concurrency][requestsPerClient];
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int clientIndex = i;
            tasks.add(() -> {
                try (Socket socket = new Socket(host, port)) {
                    socket.setTcpNoDelay(true);
                    RespWriter writer = new RespWriter(socket.getOutputStream());
                    RespReader reader = new RespReader(socket.getInputStream());

                    readyLatch.countDown();
                    startLatch.await();

                    long[] latencies = threadLatencies[clientIndex];
                    for (int req = 0; req < requestsPerClient; req++) {
                        RespValue cmd = buildCommand(commandName, clientIndex, req);
                        long start = System.nanoTime();

                        writer.write(cmd);
                        writer.flush();
                        RespValue response = reader.read();

                        long end = System.nanoTime();
                        latencies[req] = (end - start);

                        if (response == null) {
                            throw new IllegalStateException("Server returned null response");
                        }
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        if (!readyLatch.await(15, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            throw new IllegalStateException("Timeout waiting for clients to connect");
        }

        long benchmarkStartTime = System.nanoTime();
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        long benchmarkEndTime = System.nanoTime();
        executor.shutdown();

        double durationSeconds = (benchmarkEndTime - benchmarkStartTime) / 1_000_000_000.0;
        double reqPerSec = totalRequests / durationSeconds;

        long[] allLatencies = new long[totalRequests];
        int pos = 0;
        long sumNanos = 0;
        for (long[] lat : threadLatencies) {
            for (long l : lat) {
                allLatencies[pos++] = l;
                sumNanos += l;
            }
        }
        Arrays.sort(allLatencies);

        double avgLatencyMs = (sumNanos / (double) totalRequests) / 1_000_000.0;
        double p50Ms = allLatencies[(int) (totalRequests * 0.50)] / 1_000_000.0;
        double p95Ms = allLatencies[(int) (totalRequests * 0.95)] / 1_000_000.0;
        double p99Ms = allLatencies[(int) (totalRequests * 0.99)] / 1_000_000.0;
        double maxMs = allLatencies[totalRequests - 1] / 1_000_000.0;

        return new BenchmarkResult(
                commandName,
                concurrency,
                totalRequests,
                durationSeconds,
                reqPerSec,
                avgLatencyMs,
                p50Ms,
                p95Ms,
                p99Ms,
                maxMs
        );
    }

    private static RespValue buildCommand(String commandName, int clientIndex, int reqIndex) {
        String upper = commandName.toUpperCase();
        return switch (upper) {
            case "PING" -> RespValue.array(RespValue.bulkString("PING"));
            case "SET" -> {
                String key = "bench:key:" + (reqIndex % 1000);
                String val = "bench:val:" + clientIndex + ":" + reqIndex;
                yield RespValue.array(
                        RespValue.bulkString("SET"),
                        RespValue.bulkString(key),
                        RespValue.bulkString(val)
                );
            }
            case "GET" -> {
                String key = "bench:key:" + (reqIndex % 1000);
                yield RespValue.array(
                        RespValue.bulkString("GET"),
                        RespValue.bulkString(key)
                );
            }
            case "INCR" -> {
                String key = "bench:counter:" + (reqIndex % 50);
                yield RespValue.array(
                        RespValue.bulkString("INCR"),
                        RespValue.bulkString(key)
                );
            }
            case "LPUSH" -> {
                String key = "bench:list:" + clientIndex;
                yield RespValue.array(
                        RespValue.bulkString("LPUSH"),
                        RespValue.bulkString(key),
                        RespValue.bulkString("item:" + reqIndex)
                );
            }
            default -> throw new IllegalArgumentException("Unsupported benchmark command: " + commandName);
        };
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;
        int requestsPerClient = args.length > 2 ? Integer.parseInt(args[2]) : 2000;

        int[] clientTiers = {1, 10, 50, 100};
        String[] commands = {"PING", "SET", "GET", "INCR"};

        System.out.println("==========================================================================================");
        System.out.println("  JedisLite Benchmark Harness — Target: " + host + ":" + port);
        System.out.println("==========================================================================================");
        System.out.println(BenchmarkResult.formatHeader());

        for (String cmd : commands) {
            for (int clients : clientTiers) {
                BenchmarkResult res = runBenchmark(host, port, cmd, clients, requestsPerClient);
                System.out.println(res.formatRow());
            }
            System.out.println(BenchmarkResult.formatFooter());
        }
    }
}

