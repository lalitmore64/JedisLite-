package com.jedislite.server;

import java.io.Closeable;
import java.io.IOException;

public interface RedisServer extends Closeable {

    void start() throws IOException;

    int getPort();

    boolean isRunning();

    void stop() throws IOException;

    @Override
    default void close() throws IOException {
        stop();
    }
}

