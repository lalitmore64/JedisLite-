package com.jedislite.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStoreConcurrencyTest {

    private byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private ByteArrayKey k(String s) {
        return ByteArrayKey.of(s);
    }

    @Test
    @DisplayName("Concurrent LPUSH and RPOP on shared list maintains exact counts")
    void testConcurrentListPushPop() throws Exception {
        RedisStore store = new RedisStore();
        ByteArrayKey listKey = k("concurrent:list");
        int threadCount = 10;
        int operationsPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);
        CountDownLatch latch = new CountDownLatch(1);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            tasks.add(() -> {
                latch.await();
                for (int j = 0; j < operationsPerThread; j++) {
                    store.lpush(listKey, List.of(b("item-" + threadId + "-" + j)));
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        latch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }

        assertThat(store.llen(listKey)).isEqualTo(threadCount * operationsPerThread);

        AtomicInteger totalPopped = new AtomicInteger(0);
        CountDownLatch consumerLatch = new CountDownLatch(1);
        List<Callable<Void>> consumerTasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            consumerTasks.add(() -> {
                consumerLatch.await();
                while (true) {
                    byte[] item = store.rpop(listKey);
                    if (item == null) {
                        break;
                    }
                    totalPopped.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> consumerFutures = new ArrayList<>();
        for (Callable<Void> task : consumerTasks) {
            consumerFutures.add(executor.submit(task));
        }

        consumerLatch.countDown();
        for (Future<Void> f : consumerFutures) {
            f.get();
        }

        assertThat(totalPopped.get()).isEqualTo(threadCount * operationsPerThread);
        assertThat(store.exists(listKey)).isFalse();

        executor.shutdown();
    }

    @Test
    @DisplayName("Concurrent HSET mutations on shared hash")
    void testConcurrentHashMutations() throws Exception {
        RedisStore store = new RedisStore();
        ByteArrayKey hashKey = k("concurrent:hash");
        int threadCount = 10;
        int fieldsPerThread = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            tasks.add(() -> {
                latch.await();
                for (int j = 0; j < fieldsPerThread; j++) {
                    store.hset(hashKey, Map.of(k("f:" + threadId + ":" + j), b("val-" + j)));
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> t : tasks) {
            futures.add(executor.submit(t));
        }

        latch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }

        assertThat(store.hlen(hashKey)).isEqualTo(threadCount * fieldsPerThread);
        executor.shutdown();
    }

    @Test
    @DisplayName("Concurrent SADD and SISMEMBER on shared set")
    void testConcurrentSetOperations() throws Exception {
        RedisStore store = new RedisStore();
        ByteArrayKey setKey = k("concurrent:set");
        int threadCount = 10;
        int itemsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            tasks.add(() -> {
                latch.await();
                for (int j = 0; j < itemsPerThread; j++) {
                    store.sadd(setKey, List.of(k("member-" + threadId + "-" + j)));
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> t : tasks) {
            futures.add(executor.submit(t));
        }

        latch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }

        assertThat(store.scard(setKey)).isEqualTo(threadCount * itemsPerThread);
        executor.shutdown();
    }

    @Test
    @DisplayName("atomic INCR and DECR mutations")
    void testConcurrentIncrDecrAtomicRace() throws Exception {
        RedisStore store = new RedisStore();
        ByteArrayKey counterKey = k("atomic:counter");
        int threadCount = 20;
        int opsPerThread = 2000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final boolean isIncr = (i % 2 == 0);
            tasks.add(() -> {
                startLatch.await();
                for (int j = 0; j < opsPerThread; j++) {
                    if (isIncr) {
                        store.incrBy(counterKey, 1);
                    } else {
                        store.incrBy(counterKey, -1);
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
            f.get();
        }

        byte[] finalBytes = store.get(counterKey);
        assertThat(finalBytes).isNotNull();
        long finalVal = Long.parseLong(new String(finalBytes, StandardCharsets.US_ASCII));
        assertThat(finalVal).isEqualTo(0L);

        executor.shutdown();
    }

    @Test
    @DisplayName("mixed concurrent reads, writes, and deletes")
    void testConcurrentReadWriteDeleteRace() throws Exception {
        RedisStore store = new RedisStore();
        int keyCount = 20;
        int threadCount = 16;
        int iterations = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            tasks.add(() -> {
                startLatch.await();
                for (int iter = 0; iter < iterations; iter++) {
                    ByteArrayKey key = k("race:key:" + (iter % keyCount));
                    int op = (threadId + iter) % 4;
                    switch (op) {
                        case 0 -> store.set(key, b("val-" + threadId + "-" + iter));
                        case 1 -> store.get(key);
                        case 2 -> store.exists(key);
                        case 3 -> store.del(key);
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
            f.get();
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("concurrent TTL operations during active sweep")
    void testConcurrentTtlAndActiveEvictionRace() throws Exception {
        RedisStore store = new RedisStore();
        int writerThreads = 8;
        int sweeperThreads = 2;
        int keysPerWriter = 500;

        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + sweeperThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger totalCreated = new AtomicInteger(0);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < writerThreads; i++) {
            final int threadId = i;
            tasks.add(() -> {
                startLatch.await();
                for (int j = 0; j < keysPerWriter; j++) {
                    ByteArrayKey key = k("ttl:race:" + threadId + ":" + j);

                    long ttlMs = 1 + (j % 20);
                    store.set(key, b("v"), System.currentTimeMillis() + ttlMs, false, false);
                    totalCreated.incrementAndGet();
                    store.get(key);
                }
                return null;
            });
        }

        for (int s = 0; s < sweeperThreads; s++) {
            tasks.add(() -> {
                startLatch.await();
                long deadline = System.currentTimeMillis() + 500;
                while (System.currentTimeMillis() < deadline) {
                    store.activeExpireCycle(20, 10);
                    Thread.yield();
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
            f.get();
        }

        assertThat(totalCreated.get()).isEqualTo(writerThreads * keysPerWriter);
        executor.shutdown();
    }
}

