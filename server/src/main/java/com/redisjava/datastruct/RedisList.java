package com.redisjava.datastruct;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Redis LIST data structure.
 * <p>
 * Backed by a Java {@link ArrayDeque} (doubly-linked-list semantics).
 * Stores raw {@code byte[]} elements — on-heap for simplicity; the
 * Ticketmaster waiting-room typically holds at most a few thousand
 * queue entries so off-heap allocation is not required here.
 * </p>
 *
 * <h3>Supported operations</h3>
 * <ul>
 *   <li>LPUSH  – push one element to the head, O(1)</li>
 *   <li>RPUSH  – push one element to the tail, O(1)</li>
 *   <li>LPOP   – pop from the head, O(1)</li>
 *   <li>RPOP   – pop from the tail, O(1)</li>
 *   <li>LLEN   – list length, O(1)</li>
 *   <li>LRANGE – range slice [start, stop] with negative-index support, O(N)</li>
 * </ul>
 */
public class RedisList {

    /** Underlying deque — ArrayDeque is faster than LinkedList for our usage. */
    private final Deque<byte[]> deque = new ArrayDeque<>();

    // ── Mutators ─────────────────────────────────────────────────────────

    /**
     * Prepends {@code value} to the head of the list.
     *
     * @param value Element to prepend.
     * @return New list length.
     */
    public long lpush(byte[] value) {
        deque.addFirst(value);
        return deque.size();
    }

    /**
     * Appends {@code value} to the tail of the list.
     *
     * @param value Element to append.
     * @return New list length.
     */
    public long rpush(byte[] value) {
        deque.addLast(value);
        return deque.size();
    }

    /**
     * Removes and returns the first element (head).
     *
     * @return Element bytes, or {@code null} when empty.
     */
    public byte[] lpop() {
        return deque.pollFirst();
    }

    /**
     * Removes and returns the last element (tail).
     *
     * @return Element bytes, or {@code null} when empty.
     */
    public byte[] rpop() {
        return deque.pollLast();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /**
     * Returns the number of elements in the list.
     *
     * @return List length.
     */
    public long llen() {
        return deque.size();
    }

    /**
     * Returns a sub-list from {@code start} to {@code stop} (inclusive).
     * <p>
     * Supports negative indices: {@code -1} means the last element,
     * {@code -2} means the second to last, etc.  This follows the same
     * semantics as Redis LRANGE.
     * </p>
     *
     * @param start Start index (may be negative).
     * @param stop  Stop index (may be negative).
     * @return Array of matched elements, never {@code null}.
     */
    public byte[][] lrange(long start, long stop) {
        int size = deque.size();
        if (size == 0) {
            return new byte[0][];
        }

        // Normalize negative indices
        long s = start < 0 ? Math.max(0, size + start) : start;
        long e = stop  < 0 ? size + stop                 : stop;

        // Clamp to valid range
        if (s >= size || e < 0 || s > e) {
            return new byte[0][];
        }
        e = Math.min(e, size - 1);

        int count = (int) (e - s + 1);
        byte[][] result = new byte[count][];

        Iterator<byte[]> it = deque.iterator();
        int idx = 0;
        int ri  = 0;
        while (it.hasNext()) {
            byte[] elem = it.next();
            if (idx >= s && idx <= e) {
                result[ri++] = elem;
            }
            idx++;
            if (idx > e) break;
        }
        return result;
    }

    /**
     * Returns {@code true} when the list has no elements.
     *
     * @return {@code true} if empty.
     */
    public boolean isEmpty() {
        return deque.isEmpty();
    }
}
