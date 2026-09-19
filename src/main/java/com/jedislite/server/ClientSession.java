package com.jedislite.server;

import com.jedislite.engine.CommandDispatcher;
import com.jedislite.resp.RespFrameDecoder;
import com.jedislite.resp.RespReader;
import com.jedislite.resp.RespValue;
import com.jedislite.resp.RespWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;

public class ClientSession {

    private static final int INITIAL_BUFFER_CAPACITY = 8192;
    private static final int MAX_BUFFER_CAPACITY = 64 * 1024 * 1024;

    private final SocketChannel channel;
    private final CommandDispatcher dispatcher;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(INITIAL_BUFFER_CAPACITY);
    private final Queue<ByteBuffer> writeQueue = new ArrayDeque<>();

    private byte[] accumulatedBuffer = new byte[INITIAL_BUFFER_CAPACITY];
    private int accumulatedCount = 0;

    public ClientSession(SocketChannel channel, CommandDispatcher dispatcher) {
        this.channel = Objects.requireNonNull(channel, "SocketChannel cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "CommandDispatcher cannot be null");
    }

    public boolean handleRead(SelectionKey key) throws IOException {
        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);
        if (bytesRead == -1) {
            return false;
        }
        if (bytesRead == 0) {
            return true;
        }

        readBuffer.flip();
        appendData(readBuffer);

        processFrames(key);

        return true;
    }

    private void appendData(ByteBuffer src) {
        int length = src.remaining();
        ensureCapacity(accumulatedCount + length);
        src.get(accumulatedBuffer, accumulatedCount, length);
        accumulatedCount += length;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > MAX_BUFFER_CAPACITY) {
            throw new IllegalStateException("Client accumulated buffer exceeded max limit (" + MAX_BUFFER_CAPACITY + " bytes)");
        }
        if (minCapacity > accumulatedBuffer.length) {
            int newCapacity = Math.max(accumulatedBuffer.length * 2, minCapacity);
            accumulatedBuffer = Arrays.copyOf(accumulatedBuffer, newCapacity);
        }
    }

    private void processFrames(SelectionKey key) throws IOException {
        while (accumulatedCount > 0) {
            int frameLen = RespFrameDecoder.findCompleteFrame(accumulatedBuffer, 0, accumulatedCount);
            if (frameLen == -1) {

                break;
            }

            byte[] frameBytes = Arrays.copyOfRange(accumulatedBuffer, 0, frameLen);
            RespValue request = RespReader.decode(frameBytes);
            RespValue response = dispatcher.dispatch(request);

            byte[] responseBytes = RespWriter.serialize(response);
            writeQueue.add(ByteBuffer.wrap(responseBytes));

            int remaining = accumulatedCount - frameLen;
            if (remaining > 0) {
                System.arraycopy(accumulatedBuffer, frameLen, accumulatedBuffer, 0, remaining);
            }
            accumulatedCount = remaining;
        }

        flushWrites(key);
    }

    public void flushWrites(SelectionKey key) throws IOException {
        while (!writeQueue.isEmpty()) {
            ByteBuffer buf = writeQueue.peek();
            channel.write(buf);
            if (buf.hasRemaining()) {

                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                return;
            }
            writeQueue.poll();
        }

        if (key.isValid() && (key.interestOps() & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
        writeQueue.clear();
        accumulatedCount = 0;
    }
}

