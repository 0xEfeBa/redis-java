package com.redisjava.memory;

import com.redisjava.util.MemoryConstants;
import static com.redisjava.util.MemoryConstants.*;

/**
 * Represents a large block of Off-Heap Memory (16MB).
 * <p>
 * A Chunk divides itself into equal-sized slots (e.g., 64 bytes) and tracks
 * usage
 * using a bitmap.
 * </p>
 * <p>
 * <b>WARNING:</b> {@value MemoryConstants#THREAD_SAFETY_WARNING}
 * </p>
 */
public class Chunk {

    private final long baseAddress;
    private final long dataStartAddress;
    private final int slotSize;
    private final int maxSlots;

    private final long[] bitmap;
    private final int bitmapLength;
    private int freeCount;
    private int searchWordIndex = 0;

    // Package-private for visibility from SlabCache, but accessed via
    // getters/setters now
    private Chunk prev;
    private Chunk next;

    private int chunkId = -1;

    /**
     * Creates a new Chunk initialized for a specific slot size.
     *
     * @param slotSize The size of each object slot in this chunk.
     */
    public Chunk(int slotSize) {

        int dataSize = CHUNK_SIZE - CHUNK_HEADER_SIZE;

        if (dataSize % slotSize != 0) {
            dataSize = (dataSize / slotSize) * slotSize;
        }

        this.slotSize = slotSize;
        this.maxSlots = dataSize / slotSize;
        this.freeCount = maxSlots;

        // BITS_PER_LONG for 64, DIV_64_SHIFT for division optimization
        this.bitmapLength = (maxSlots + (BITS_PER_LONG - 1)) >> DIV_64_SHIFT;
        this.bitmap = new long[bitmapLength];

        this.baseAddress = DirectBufferUtils.allocate(CHUNK_SIZE);
        this.dataStartAddress = baseAddress + CHUNK_HEADER_SIZE;

        DirectBufferUtils.putLong(baseAddress, UNALLOCATED);
    }

    public void setChunkId(int id) {
        this.chunkId = id;
        DirectBufferUtils.putLong(baseAddress, id);
    }

    public int getChunkId() {
        return chunkId;
    }

    /**
     * 
     * Allocates a slot within this chunk.
     * 
     * @return The absolute address of the allocated slot, or -1 if full.
     */
    public long alloc() {
        if (freeCount == 0) {
            return -1;
        }

        int wordIndex = searchWordIndex;
        int startWord = wordIndex;

        do {
            long word = bitmap[wordIndex];

            if (word != ALL_ONES) {
                int bitIndex = Long.numberOfTrailingZeros(~word);
                // wordIndex * 64 -> wordIndex << 6
                int slotIndex = (wordIndex << DIV_64_SHIFT) + bitIndex;

                if (slotIndex < maxSlots) {

                    bitmap[wordIndex] = word | (1L << bitIndex);
                    freeCount--;

                    searchWordIndex = wordIndex;

                    return dataStartAddress + ((long) slotIndex * slotSize);
                }
            }

            wordIndex = (wordIndex + 1) % bitmapLength;
        } while (wordIndex != startWord);

        return -1;
    }

    /**
     * Frees a slot at the given address.
     * 
     * @param address Absolute memory address.
     * @return true if successful, false if address is out of bounds.
     */
    public boolean free(long address) {

        if (address < dataStartAddress || address >= dataStartAddress + ((long) maxSlots * slotSize)) {
            return false;
        }

        long offset = address - dataStartAddress;

        if (offset % slotSize != 0) {
            throw new IllegalArgumentException("Address " + address + " is not aligned to slot size " + slotSize);
        }

        int slotIndex = (int) (offset / slotSize);
        // slotIndex / 64 -> slotIndex >> 6
        int wordIndex = slotIndex >> DIV_64_SHIFT;
        // slotIndex % 64 -> slotIndex & 63
        int bitIndex = slotIndex & MOD_64_MASK;

        long mask = 1L << bitIndex;
        long word = bitmap[wordIndex];

        if ((word & mask) == 0) {
            throw new IllegalStateException(
                    "Double-free detected at address: " + address + " (slot: " + slotIndex + ")");
        }

        bitmap[wordIndex] = word & ~mask;
        freeCount++;

        searchWordIndex = wordIndex;

        return true;
    }

    public boolean containsAddress(long address) {
        return address >= baseAddress && address < baseAddress + CHUNK_SIZE;
    }

    public boolean isFull() {
        return freeCount == 0;
    }

    public boolean isEmpty() {
        return freeCount == maxSlots;
    }

    public int getFreeCount() {
        return freeCount;
    }

    public void destroy() {
        DirectBufferUtils.free(baseAddress);
    }

    public long getBaseAddress() {
        return baseAddress;
    }

    public int getSlotSize() {

        return slotSize;

    }

    // Encapsulation Methods for Linked List
    public Chunk getPrev() {
        return prev;
    }

    public void setPrev(Chunk prev) {
        this.prev = prev;
    }

    public Chunk getNext() {
        return next;
    }

    public void setNext(Chunk next) {
        this.next = next;
    }
}
