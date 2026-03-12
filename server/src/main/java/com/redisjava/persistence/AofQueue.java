package com.redisjava.persistence;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MPSC (Multi-Producer Single-Consumer) queue for async AOF writes.
 *
 * <h3>Design</h3>
 * <p>
 * The event-loop thread (producer) serialises command bytes and
 * {@link #offer(byte[])} them here.  The {@link AofWriterThread}
 * (consumer) drains the queue on a background thread, eliminating
 * fsync latency from the hot path.
 * </p>
 *
 * <p>
 * {@link ConcurrentLinkedQueue} is lock-free and suits an SPSC / MPSC
 * workload: the enqueue path is O(1) and non-blocking.
 * </p>
 */
public class AofQueue {

    // ── Singleton ──────────────────────────────────────────────────────────

    private static final AofQueue INSTANCE = new AofQueue();

    public static AofQueue getInstance() {
        return INSTANCE;
    }

    // ── State ──────────────────────────────────────────────────────────────

    private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();

    /** Approximate number of bytes waiting in the queue (for back-pressure). */
    private final AtomicLong pendingBytes = new AtomicLong(0);

    /** Soft limit: if pendingBytes exceeds this, the producer may block/drop. */
    private static final long MAX_PENDING_BYTES = 32L * 1024 * 1024; // 32 MB

    // ── API ────────────────────────────────────────────────────────────────

    /**
     * Enqueues pre-serialised RESP bytes for async writing.
     *
     * <p>Returns {@code true} if the entry was accepted, {@code false} if the
     * queue is above the soft limit (caller should apply back-pressure or log).
     *
     * @param entry Pre-formatted RESP bytes.
     * @return true if enqueued, false if back-pressured.
     */
    public boolean offer(byte[] entry) {
        if (entry == null || entry.length == 0) return false;
        if (pendingBytes.get() > MAX_PENDING_BYTES) return false; // back-pressure
        queue.offer(entry);
        pendingBytes.addAndGet(entry.length);
        return true;
    }

    /**
     * Dequeues the next entry, or {@code null} if the queue is empty.
     *
     * @return Next entry or null.
     */
    public byte[] poll() {
        byte[] entry = queue.poll();
        if (entry != null) {
            pendingBytes.updateAndGet(v -> Math.max(0, v - entry.length));
        }
        return entry;
    }

    /**
     * @return true if the queue is empty.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * @return Approximate number of pending entries.
     */
    public int size() {
        return queue.size();
    }

    /**
     * @return Approximate pending bytes.
     */
    public long getPendingBytes() {
        return pendingBytes.get();
    }

    /**
     * Clears all pending entries. <strong>For testing only.</strong>
     */
    public void reset() {
        queue.clear();
        pendingBytes.set(0);
    }
}
