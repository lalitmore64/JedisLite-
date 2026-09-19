package com.jedislite;

import com.jedislite.server.NioRedisServer;

import java.io.IOException;

public class JedisLiteServer {

    public static final int DEFAULT_PORT = 6379;

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default: " + DEFAULT_PORT);
            }
        }

        NioRedisServer server = new NioRedisServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (IOException e) {
                System.err.println("Error stopping server: " + e.getMessage());
            }
        }));

        server.start();
        System.out.println("JedisLite server started on port " + port);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

