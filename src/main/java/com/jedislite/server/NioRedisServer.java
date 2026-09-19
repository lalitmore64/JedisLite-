package com.jedislite.server;

import com.jedislite.engine.CommandDispatcher;
import com.jedislite.engine.Database;
import com.jedislite.resp.RespProtocolException;
import com.jedislite.storage.ActiveExpirationSweeper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class NioRedisServer implements RedisServer {

    private final int requestedPort;
    private final CommandDispatcher dispatcher;
    private final ActiveExpirationSweeper sweeper;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Thread eventLoopThread;
    private int actualPort;

    public NioRedisServer(int port, CommandDispatcher dispatcher) {
        this.requestedPort = port;
        this.dispatcher = Objects.requireNonNull(dispatcher, "CommandDispatcher cannot be null");
        this.sweeper = new ActiveExpirationSweeper(dispatcher.getStore());
    }

    public NioRedisServer(int port) {
        this(port, new CommandDispatcher(new Database()));
    }

    @Override
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress(requestedPort));
        this.actualPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        this.running.set(true);

        dispatcher.getDatabase().getSnapshotManager().loadIfExists();
        dispatcher.getDatabase().getSnapshotManager().startPeriodicSave();
        this.sweeper.start();

        this.eventLoopThread = Thread.ofPlatform()
                .name("redis-nio-loop-" + actualPort)
                .start(this::runEventLoop);
    }

    private void runEventLoop() {
        while (running.get()) {
            try {
                int selected = selector.select(100);
                if (!running.get()) {
                    break;
                }
                if (selected == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectedKeys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        }
                        if (key.isValid() && key.isReadable()) {
                            handleRead(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException | RespProtocolException e) {
                        closeSession(key);
                    }
                }
            } catch (ClosedChannelException e) {

                break;
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("I/O error in event loop: " + e.getMessage());
                }
            }
        }

        cleanupResources();
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = ssc.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            ClientSession session = new ClientSession(clientChannel, dispatcher);
            clientChannel.register(selector, SelectionKey.OP_READ, session);
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        ClientSession session = (ClientSession) key.attachment();
        if (session != null) {
            boolean open = session.handleRead(key);
            if (!open) {
                closeSession(key);
            }
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        ClientSession session = (ClientSession) key.attachment();
        if (session != null) {
            session.flushWrites(key);
        }
    }

    private void closeSession(SelectionKey key) {
        key.cancel();
        ClientSession session = (ClientSession) key.attachment();
        if (session != null) {
            session.close();
        }
    }

    private void cleanupResources() {
        if (selector != null) {
            for (SelectionKey key : selector.keys()) {
                try {
                    key.channel().close();
                } catch (IOException ignored) {
                }
            }
            try {
                selector.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public int getPort() {
        if (!running.get() && serverChannel == null) {
            throw new IllegalStateException("Server has not been started");
        }
        return actualPort;
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

        if (selector != null) {
            selector.wakeup();
        }

        if (serverChannel != null && serverChannel.isOpen()) {
            serverChannel.close();
        }

        if (eventLoopThread != null) {
            try {
                eventLoopThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public CommandDispatcher getDispatcher() {
        return dispatcher;
    }
}

