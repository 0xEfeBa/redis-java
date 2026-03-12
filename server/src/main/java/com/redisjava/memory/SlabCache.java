package com.redisjava.memory;

import com.redisjava.util.MemoryConstants;

/**
 * Manages Chunks for a specific object size (e.g. 64 byte objects).
 * <p>
 * Keeps track of partial, full, and free chunks to optimize allocation speed.
 * </p>
 * <p>
 * <b>WARNING:</b> {@value MemoryConstants#THREAD_SAFETY_WARNING}
 * </p>
 */
public class SlabCache {

    private final int objectSize;
    private final MemoryManager memoryManager;

    private final ChunkListHead partialList;
    private final ChunkListHead fullList;
    private final ChunkListHead freeList;

    public SlabCache(int objectSize, MemoryManager memoryManager) {
        this.objectSize = objectSize;
        this.memoryManager = memoryManager;
        this.partialList = new ChunkListHead();
        this.fullList = new ChunkListHead();
        this.freeList = new ChunkListHead();
    }

    /**
     * Allocates an address for an object of {@code objectSize}.
     * 
     * @return Absolute memory address.
     */
    public long alloc() {
        Chunk chunk = null;

        if (!partialList.isEmpty()) {
            chunk = partialList.getFirst();
        }

        else if (!freeList.isEmpty()) {
            chunk = freeList.removeFirst();
            partialList.addFirst(chunk);
        }

        else {
            chunk = new Chunk(objectSize);
            partialList.addFirst(chunk);

            memoryManager.registerChunk(chunk);
        }

        long address = chunk.alloc();

        if (chunk.isFull()) {
            partialList.remove(chunk);
            fullList.addFirst(chunk);
        }

        return address;
    }

    /**
     * Notifies the cache that an object has been freed in the given chunk.
     * Moves the chunk between lists (Full -> Partial -> Free) if necessary.
     * 
     * @param chunk The chunk where the free occurred.
     */
    public void notifyFree(Chunk chunk) {

        if (isInList(chunk, fullList)) {
            fullList.remove(chunk);
            partialList.addFirst(chunk);
        }

        if (chunk.isEmpty()) {
            if (isInList(chunk, partialList)) {
                partialList.remove(chunk);
                freeList.addFirst(chunk);
            }
        }
    }

    private boolean isInList(Chunk chunk, ChunkListHead list) {
        // Updated to use getters
        return chunk.getPrev() != null || chunk.getNext() != null || list.head == chunk;
    }

    public int getObjectSize() {
        return objectSize;
    }

    private static class ChunkListHead {
        Chunk head;

        boolean isEmpty() {
            return head == null;
        }

        Chunk getFirst() {
            return head;
        }

        void addFirst(Chunk chunk) {
            chunk.setPrev(null);
            chunk.setNext(head);
            if (head != null) {
                head.setPrev(chunk);
            }
            head = chunk;
        }

        void remove(Chunk chunk) {
            Chunk prev = chunk.getPrev();
            Chunk next = chunk.getNext();

            if (prev != null) {
                prev.setNext(next);
            } else {
                head = next;
            }
            if (next != null) {
                next.setPrev(prev);
            }

            chunk.setPrev(null);
            chunk.setNext(null);
        }

        Chunk removeFirst() {
            if (head == null)
                return null;
            Chunk first = head;
            head = first.getNext();
            if (head != null) {
                head.setPrev(null);
            }
            first.setNext(null);
            return first;
        }
    }
}
