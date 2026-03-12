package com.redisjava.memory;

import com.redisjava.util.MemoryConstants;
import static com.redisjava.util.MemoryConstants.*;

/**
 * Main entry point for Off-Heap Memory Management.
 * <p>
 * This class coordinates Slab Allocation and Page Mapping to provide efficient,
 * zero-GC memory allocation.
 * </p>
 * <p>
 * <b>WARNING:</b> {@value MemoryConstants#THREAD_SAFETY_WARNING}
 * </p>
 */
public class MemoryManager {

    private final SlabCache[] slabs;
    private final Chunk[] allChunks;
    private final PageMap pageMap;
    private int chunkCount = 0;
    private final int maxChunks;
    private boolean isShutdown = false;

    /**
     * Creates a MemoryManager with default settings (Max 16GB).
     */
    public MemoryManager() {
        this(DEFAULT_MAX_CHUNKS);
    }

    /**
     * Creates a MemoryManager with custom maximum chunk limit.
     *
     * @param maxChunks Maximum number of 16MB chunks allowed.
     */
    public MemoryManager(int maxChunks) {
        if (maxChunks <= 0) {
            throw new IllegalArgumentException("Max chunks must be positive");
        }
        this.maxChunks = maxChunks;
        this.allChunks = new Chunk[maxChunks];
        this.pageMap = new PageMap();

        int classCount = 0;
        for (int s = MIN_SLAB_SIZE; s <= MAX_SLAB_SIZE; s *= 2) {
            classCount++;
        }

        slabs = new SlabCache[classCount];
        int index = 0;
        for (int s = MIN_SLAB_SIZE; s <= MAX_SLAB_SIZE; s *= 2) {
            slabs[index++] = new SlabCache(s, this);
        }
    }

    /**
     * Allocates off-heap memory of the requested size.
     *
     * @param size Size in bytes. Will be rounded up to the nearest slab size.
     * @return The 64-bit absolute memory address.
     * @throws IllegalArgumentException if size is invalid or too large.
     */
    public long alloc(int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Size must be positive");
        if (size > MAX_SLAB_SIZE)
            throw new IllegalArgumentException("Huge pages not supported yet (>16KB): " + size);

        SlabCache slab = getSlabCache(size);
        return slab.alloc();
    }

    /**
     * Frees the memory at the given address.
     *
     * @param address The absolute memory address returned by alloc().
     * @throws IllegalArgumentException if the address is invalid or unknown.
     */
    public void free(long address) {
        int chunkId = pageMap.get(address);

        if (chunkId == PageMap.NOT_FOUND) {
            throw new IllegalArgumentException(
                    String.format("Unknown memory address (PageMap miss): %d (0x%X), Page: %d",
                            address, address, address >>> MemoryConstants.PAGE_SHIFT));
        }
        if (chunkId >= chunkCount) {
            throw new IllegalArgumentException(String.format("Invalid chunkId: %d >= chunkCount: %d for address: %d",
                    chunkId, chunkCount, address));
        }

        Chunk targetChunk = allChunks[chunkId];

        if (targetChunk == null) {
            throw new IllegalArgumentException("Chunk is null for id: " + chunkId);
        }

        if (!targetChunk.containsAddress(address)) {
            throw new IllegalArgumentException(String.format("Chunk %d [0x%X - 0x%X] does not contain address 0x%X",
                    chunkId, targetChunk.getBaseAddress(), targetChunk.getBaseAddress() + MemoryConstants.CHUNK_SIZE,
                    address));
        }

        boolean success = targetChunk.free(address);
        if (!success) {
            throw new RuntimeException("Failed to free address " + address);
        }

        int slotSize = targetChunk.getSlotSize();
        SlabCache cache = getSlabCache(slotSize);
        if (cache != null) {
            cache.notifyFree(targetChunk);
        }
    }

    /**
     * Reallocates a block to a new size.
     *
     * @param address The existing memory address.
     * @param oldSize The current size in bytes.
     * @param newSize The requested new size in bytes.
     * @return The new memory address.
     */
    public long realloc(long address, int oldSize, int newSize) {
        if (address == 0) {
            throw new IllegalArgumentException("Address must be valid");
        }
        if (oldSize <= 0 || newSize <= 0) {
            throw new IllegalArgumentException("Sizes must be positive");
        }
        if (oldSize == newSize) {
            return address;
        }

        long newAddress = alloc(newSize);
        long bytesToCopy = Math.min(oldSize, newSize);
        DirectBufferUtils.copyMemory(address, newAddress, bytesToCopy);
        free(address);
        return newAddress;
    }

    /**
     * Registers a new chunk with the manager.
     * Internal use only (called by SlabCache).
     *
     * @param chunk The new chunk to register.
     */
    public void registerChunk(Chunk chunk) {
        if (chunkCount >= maxChunks) {
            throw new RuntimeException("Maximum chunk limit reached: " + maxChunks);
        }
        int id = chunkCount++;
        chunk.setChunkId(id);
        allChunks[id] = chunk;

        pageMap.put(chunk.getBaseAddress(), CHUNK_SIZE, id);
    }

    /**
     * Cleanups all off-heap memory.
     * Must be called on application shutdown to prevent memory leaks.
     */
    public void shutdown() {
        isShutdown = true;
        for (int i = 0; i < chunkCount; i++) {
            if (allChunks[i] != null) {
                allChunks[i].destroy();
                allChunks[i] = null;
            }
        }
        chunkCount = 0;
        pageMap.clear();
    }

    /**
     * @return true if shutdown() has been called.
     */
    public boolean isShutdown() {
        return isShutdown;
    }

    private SlabCache getSlabCache(int size) {
        int alignedSize = MIN_SLAB_SIZE;
        // Optimization: Could use bit manipulation here too, but loop is safe for now
        while (alignedSize < size) {
            alignedSize *= 2;
        }

        if (alignedSize > MAX_SLAB_SIZE) {
            throw new IllegalArgumentException("Size too large");
        }

        // Replaced magic number 5 with constant MIN_SLAB_SHIFT
        int index = Integer.numberOfTrailingZeros(alignedSize) - MIN_SLAB_SHIFT;

        if (index < 0 || index >= slabs.length) {
            return slabs[slabs.length - 1]; // Should not happen given logic above
        }
        return slabs[index];
    }

    public int getAllocatedChunkCount() {
        return chunkCount;
    }

    /**
     * @return Total memory currently used/allocated in bytes.
     */
    public long getUsedMemory() {
        return (long) chunkCount * CHUNK_SIZE;
    }

    /**
     * @return Maximum memory this manager can allocate, in bytes.
     */
    public long getMaxMemory() {
        return (long) maxChunks * CHUNK_SIZE;
    }
}
