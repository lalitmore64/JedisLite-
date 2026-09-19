package com.jedislite.server;

import com.jedislite.engine.CommandDispatcher;
import com.jedislite.engine.Database;
import com.jedislite.resp.RespProtocolException;
import com.jedislite.resp.RespReader;
import com.jedislite.resp.RespValue;
import com.jedislite.resp.RespWriter;
import com.jedislite.storage.ActiveExpirationSweeper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingRedisServer implements RedisServer {

    private final int requestedPort;
    private final CommandDispatcher dispatcher;
    private final ActiveExpirationSweeper sweeper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public BlockingRedisServer(int port, CommandDispatcher dispatcher) {
        this.requestedPort = port;
        this.dispatcher = Objects.requireNonNull(dispatcher, "CommandDispatcher cannot be null");
        this.sweeper = new ActiveExpirationSweeper(dispatcher.getStore());
    }

    public BlockingRedisServer(int port) {
        this(port, new CommandDispatcher(new Database()));
    }

    @Override
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        this.serverSocket = new ServerSocket(requestedPort);
        this.running.set(true);

        dispatcher.getDatabase().getSnapshotManager().loadIfExists();
        dispatcher.getDatabase().getSnapshotManager().startPeriodicSave();
        this.sweeper.start();

        this.acceptThread = Thread.ofPlatform()
                .name("redis-acceptor-" + serverSocket.getLocalPort())
                .start(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                activeClients.add(client);
                Thread.ofVirtual()
                        .name("redis-client-" + client.getRemoteSocketAddress())
                        .start(() -> handleClient(client));
            } catch (SocketException e) {

                if (!running.get()) {
                    break;
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (client) {
            client.setTcpNoDelay(true);
            RespReader reader = new RespReader(client.getInputStream());
            RespWriter writer = new RespWriter(client.getOutputStream());

            RespValue request;
            while (running.get() && (request = reader.read()) != null) {
                RespValue response = dispatcher.dispatch(request);
                writer.write(response);
                writer.flush();
            }
        } catch (RespProtocolException e) {
            try {
                if (!client.isClosed()) {
                    RespWriter writer = new RespWriter(client.getOutputStream());
                    writer.write(RespValue.err("Protocol error: " + e.getMessage()));
                    writer.flush();
                }
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {

        } finally {
            activeClients.remove(client);
        }
    }

    @Override
    public int getPort() {
        if (serverSocket == null) {
            throw new IllegalStateException("Server has not been started");
        }
        return serverSocket.getLocalPort();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public synchronized void stop() throws IOException {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        sweeper.stop();
        dispatcher.getDatabase().getSnapshotManager().close();

        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        for (Socket client : activeClients) {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
        activeClients.clear();

        if (acceptThread != null) {
            try {
                acceptThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public CommandDispatcher getDispatcher() {
        return dispatcher;
    }
}

