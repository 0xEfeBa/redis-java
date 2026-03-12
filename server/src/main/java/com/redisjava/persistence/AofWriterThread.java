package com.redisjava.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Background AOF writer thread.
 *
 * <h3>Design</h3>
 * <p>
 * Drains the {@link AofQueue} every {@value #DRAIN_INTERVAL_MS} ms and
 * batches entries into the AOF file.  Batching amortises the cost of
 * {@code write(2)} syscalls and, when {@code fsync} mode is
 * {@code everysec}, limits durability lag to ~1 second.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <pre>{@code
 *   AofWriterThread writer = new AofWriterThread(queue, aofPath);
 *   writer.start();
 *   // ...
 *   writer.shutdown();
 *   writer.join(5_000);
 * }</pre>
 */
public class AofWriterThread extends Thread {

    /** How long to sleep between drain cycles (milliseconds). */
    static final long DRAIN_INTERVAL_MS = 10;

    /** Write buffer size: 64 KB. */
    private static final int WRITE_BUFFER = 64 * 1024;

    // ── State ──────────────────────────────────────────────────────────────

    private final AofQueue queue;
    private final Path aofPath;
    private volatile boolean running = true;

    private FileChannel channel;
    private final ByteBuffer writeBuffer = ByteBuffer.allocateDirect(WRITE_BUFFER);

    /** Total entries written (for monitoring). */
    private volatile long totalWritten = 0;
    /** Total bytes written (for monitoring). */
    private volatile long totalBytes = 0;

    // ── Construction ──────────────────────────────────────────────────────

    /**
     * Creates a new writer thread.
     *
     * @param queue   The source queue.
     * @param aofPath Path to the AOF file.
     */
    public AofWriterThread(AofQueue queue, Path aofPath) {
        super("aof-writer");
        setDaemon(true);
        this.queue = queue;
        this.aofPath = aofPath;
    }

    // ── Thread lifecycle ──────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            open();
            while (running) {
                drainAndFlush();
                try {
                    Thread.sleep(DRAIN_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("[AofWriterThread] fatal: " + e.getMessage());
        } finally {
            // Final drain before exit
            try {
                drainAndFlush();
            } catch (IOException ignored) {
            }
            close();
        }
    }

    /**
     * Signals the thread to stop after finishing the current drain cycle.
     */
    public void shutdown() {
        running = false;
        interrupt();
    }

    // ── Synchronous drain (for testing / forced flush) ─────────────────────

    /**
     * Drains all current queue entries and flushes the write buffer to disk.
     *
     * <p>May be called from any thread for testing, or from the writer
     * thread itself.
     *
     * @throws IOException If a write error occurs.
     */
    public void drainNow() throws IOException {
        if (channel == null) {
            open();
        }
        drainAndFlush();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void open() throws IOException {
        channel = FileChannel.open(aofPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
    }

    private void drainAndFlush() throws IOException {
        if (channel == null) return;

        byte[] entry;
        while ((entry = queue.poll()) != null) {
            writeEntry(entry);
            totalWritten++;
        }
        flushBuffer();
    }

    private void writeEntry(byte[] entry) throws IOException {
        int offset = 0;
        while (offset < entry.length) {
            int remaining = entry.length - offset;
            int space = writeBuffer.remaining();

            if (space == 0) {
                flushBuffer();
                space = writeBuffer.remaining();
            }

            int toCopy = Math.min(remaining, space);
            writeBuffer.put(entry, offset, toCopy);
            offset += toCopy;
            totalBytes += toCopy;
        }
    }

    private void flushBuffer() throws IOException {
        if (writeBuffer.position() == 0) return;
        writeBuffer.flip();
        while (writeBuffer.hasRemaining()) {
            channel.write(writeBuffer);
        }
        writeBuffer.clear();
    }

    private void close() {
        if (channel == null) return;
        try {
            channel.close();
        } catch (IOException ignored) {
        }
        channel = null;
    }

    // ── Monitoring ────────────────────────────────────────────────────────

    /**
     * @return Total number of entries written to disk.
     */
    public long getTotalWritten() {
        return totalWritten;
    }

    /**
     * @return Total bytes written to disk.
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * @return true if the writer thread is running.
     */
    public boolean isRunning() {
        return running;
    }
}
