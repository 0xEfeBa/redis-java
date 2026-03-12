package com.redisjava.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;

import com.redisjava.memory.DirectBufferUtils;

/**
 * Represents a single client TCP connection.
 * <p>
 * Wraps a {@link SocketChannel} and manages read/write buffers.
 * This class is NOT thread-safe and is designed to be used within
 * a single-threaded event loop.
 * </p>
 */
public class Connection {

    /** Buffer size: 16KB (power of 2 for optimal performance) */
    private static final int BUFFER_SIZE = 16 * 1024;

    /** Maximum write queue size: 1MB (slow client protection) */
    private static final int MAX_QUEUE_BYTES = 1024 * 1024;

    /** Default idle timeout in milliseconds: 60 seconds */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 60_000;

    private final SocketChannel channel;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private final SelectionKey selectionKey;
    private final long idleTimeoutMs;

    /**
     * Write queue for backpressure handling.
     * When writeBuffer is full, data is queued here.
     */
    private final ArrayDeque<byte[]> writeQueue = new ArrayDeque<>();
    private int queuedBytes = 0;

    private long lastActivityTime;
    private boolean closed = false;
    private boolean closeRequested = false;

    /**
     * Pipelining batch mode flag.
     * When true, write() enqueues data but defers OP_WRITE registration
     * and buffer flushing until endBatch() is called.
     * This reduces the number of syscalls and selector wakeups when a
     * single TCP read delivers many back-to-back commands.
     */
    private boolean batchMode = false;

    /**
     * Creates a new Connection with default idle timeout.
     *
     * @param channel      The underlying NIO socket channel.
     * @param selectionKey The selection key for this channel.
     */
    public Connection(SocketChannel channel, SelectionKey selectionKey) {
        this(channel, selectionKey, DEFAULT_IDLE_TIMEOUT_MS);
    }

    /**
     * Creates a new Connection with custom idle timeout.
     *
     * @param channel       The underlying NIO socket channel.
     * @param selectionKey  The selection key for this channel.
     * @param idleTimeoutMs Idle timeout in milliseconds.
     */
    public Connection(SocketChannel channel, SelectionKey selectionKey, long idleTimeoutMs) {
        this.channel = channel;
        this.selectionKey = selectionKey;
        this.idleTimeoutMs = idleTimeoutMs;

        // DirectByteBuffer for zero-copy I/O
        this.readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * Reads available data from the socket into the read buffer.
     *
     * @return Number of bytes read, or -1 if connection closed.
     * @throws IOException If an I/O error occurs.
     */
    public int read() throws IOException {
        return channel.read(readBuffer);
    }

    /**
     * Writes data to the connection's write buffer or queue.
     * <p>
     * If the buffer has space, data is written directly.
     * Otherwise, data is queued for later flush (backpressure).
     * </p>
     *
     * @param data The data to write.
     */
    /**
     * Begins a pipeline batch.
     * <p>
     * Subsequent {@link #write(byte[])} calls will enqueue data without
     * flushing or registering OP_WRITE interest. Call {@link #endBatch()}
     * after all commands in the batch have been processed.
     * </p>
     */
    public void beginBatch() {
        batchMode = true;
    }

    /**
     * Ends a pipeline batch and flushes all accumulated response data.
     * <p>
     * Moves queued data to the write buffer and registers OP_WRITE once,
     * replacing per-command selector wakeups with a single registration.
     * </p>
     */
    public void endBatch() {
        batchMode = false;
        if (closed || closeRequested) return;

        flushQueueToBuffer();

        if ((writeBuffer != null) && (writeBuffer.position() > 0 || !writeQueue.isEmpty())) {
            if (selectionKey != null) {
                int ops = selectionKey.interestOps();
                if ((ops & SelectionKey.OP_WRITE) == 0) {
                    selectionKey.interestOps(ops | SelectionKey.OP_WRITE);
                }
            }
        }
    }

    public void write(byte[] data) {
        if (closed) {
            return;
        }

        enqueue(data);
        if (closeRequested) {
            return;
        }

        // In batch mode, defer flush and OP_WRITE registration to endBatch()
        if (batchMode) {
            return;
        }

        flushQueueToBuffer();

        // Register interest in OP_WRITE if we have data to send
        if (writeBuffer != null && selectionKey != null
                && (writeBuffer.position() > 0 || !writeQueue.isEmpty())) {
            int ops = selectionKey.interestOps();
            if ((ops & SelectionKey.OP_WRITE) == 0) {
                selectionKey.interestOps(ops | SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * Writes data from an off-heap address into the connection buffers.
     *
     * @param address Off-heap address to read from.
     * @param length  Number of bytes to write.
     */
    public void writeFromAddress(long address, int length) {
        if (closed || length <= 0) {
            return;
        }

        int written = 0;
        while (written < length && !closeRequested) {
            int remaining = length - written;
            int bufferRemaining = writeBuffer.remaining();

            if (bufferRemaining > 0) {
                int toCopy = Math.min(bufferRemaining, remaining);
                for (int i = 0; i < toCopy; i++) {
                    writeBuffer.put(DirectBufferUtils.getByte(address + written + i));
                }
                written += toCopy;
                continue;
            }

            int chunk = Math.min(remaining, BUFFER_SIZE);
            byte[] data = new byte[chunk];
            for (int i = 0; i < chunk; i++) {
                data[i] = DirectBufferUtils.getByte(address + written + i);
            }
            enqueue(data);
            written += chunk;
        }

        if (writeBuffer.position() > 0 || !writeQueue.isEmpty()) {
            int ops = selectionKey.interestOps();
            if ((ops & SelectionKey.OP_WRITE) == 0) {
                selectionKey.interestOps(ops | SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * Transfers data from write queue to write buffer.
     */
    private void flushQueueToBuffer() {
        while (!writeQueue.isEmpty()) {
            byte[] data = writeQueue.peekFirst();
            if (writeBuffer.remaining() >= data.length) {
                writeBuffer.put(data);
                writeQueue.pollFirst();
                queuedBytes -= data.length;
            } else {
                // Buffer full, keep remaining in queue
                break;
            }
        }
    }

    private void enqueue(byte[] data) {
        if (queuedBytes + data.length > MAX_QUEUE_BYTES) {
            closeRequested = true;
            return;
        }
        writeQueue.addLast(data);
        queuedBytes += data.length;
    }

    /**
     * Flushes the write buffer to the socket.
     *
     * @return true if all data was written, false if more remains.
     * @throws IOException If an I/O error occurs.
     */
    public boolean flush() throws IOException {
        writeBuffer.flip();
        channel.write(writeBuffer);

        if (writeBuffer.hasRemaining()) {
            // Not all data was written, compact and keep OP_WRITE
            writeBuffer.compact();
            return false;
        } else {
            // All buffer data written, clear and try to flush more from queue
            writeBuffer.clear();
            flushQueueToBuffer();

            if (writeBuffer.position() > 0) {
                // More data was added from queue, keep OP_WRITE
                return false;
            }

            // All data written, remove OP_WRITE interest
            int ops = selectionKey.interestOps();
            selectionKey.interestOps(ops & ~SelectionKey.OP_WRITE);
            return true;
        }
    }

    /**
     * Closes the connection and releases resources.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        writeQueue.clear();

        try {
            selectionKey.cancel();
            channel.close();
        } catch (IOException e) {
            // Ignore close errors - connection is being torn down anyway
        }
    }

    /**
     * Checks if this connection has been idle too long.
     *
     * @param now Current time in milliseconds.
     * @return true if idle timeout exceeded.
     */
    public boolean isIdleTimeout(long now) {
        return (now - lastActivityTime) > idleTimeoutMs;
    }

    /**
     * @return The read buffer (for handler to process).
     */
    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    /**
     * @return The underlying socket channel.
     */
    public SocketChannel getChannel() {
        return channel;
    }

    /**
     * @return The selection key for this connection.
     */
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    /**
     * @return true if connection is closed.
     */
    public boolean isClosed() {
        return closed;
    }

    public boolean isCloseRequested() {
        return closeRequested;
    }

    /**
     * Updates the last activity timestamp with cached time.
     * Call this from event loop instead of using System.currentTimeMillis() per
     * operation.
     *
     * @param now Cached current time from event loop.
     */
    public void updateActivity(long now) {
        this.lastActivityTime = now;
    }

    /**
     * @return true if there is pending data to write.
     */
    public boolean hasPendingWrites() {
        return writeBuffer.position() > 0 || !writeQueue.isEmpty();
    }

    /**
     * Writes a RESP Error.
     */
    public void writeError(String message) {
        String error = "-" + message + "\r\n";
        write(error.getBytes());
    }

    /**
     * Writes a RESP Integer.
     */
    public void writeInteger(long value) {
        String integer = ":" + value + "\r\n";
        write(integer.getBytes());
    }

    /**
     * Writes a RESP Bulk String.
     */
    public void writeBulkString(byte[] data) {
        if (data == null) {
            writeNullBulkString();
            return;
        }
        String header = "$" + data.length + "\r\n";
        write(header.getBytes());
        write(data);
        write(new byte[] { '\r', '\n' });
    }

    /**
     * Writes a RESP Null Bulk String.
     */
    public void writeNullBulkString() {
        write("$-1\r\n".getBytes());
    }

    /**
     * Writes a RESP Array header.
     */
    public void writeArrayStart(int size) {
        String header = "*" + size + "\r\n";
        write(header.getBytes());
    }

    /**
     * Writes an empty array or null array.
     */
    public void writeArray(com.redisjava.protocol.RespToken[] tokens) {
        if (tokens == null) {
            write("*-1\r\n".getBytes());
            return;
        }
        writeArrayStart(tokens.length);
        // Note: This assumes caller handles writing elements, or we add logic here.
        // For simplicity, usually we write header then loop.
        // But for empty array helper:
        if (tokens.length == 0) {
            return;
        }
        // Logic to write tokens recursively could be added here but keeping it simple
        // for now.
    }
}
