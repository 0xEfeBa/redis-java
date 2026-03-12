package com.redisjava.util;

/**
 * Central configuration constants for the Memory Management system.
 */
public class MemoryConstants {

    private MemoryConstants() {
        // Prevent instantiation
    }

    /** Minimum slab size (32 bytes). */
    public static final int MIN_SLAB_SIZE = 32;

    /** Minimum slab shift for Log2 calculation (2^5 = 32). */
    public static final int MIN_SLAB_SHIFT = 5;

    /** Maximum slab size (16KB). */
    public static final int MAX_SLAB_SIZE = 16 * 1024;

    /** Size of a single memory chunk (16MB). */
    public static final int CHUNK_SIZE = 16 * 1024 * 1024;

    /** Header size for Chunk metadata. */
    public static final int CHUNK_HEADER_SIZE = 8;

    /** Default maximum number of chunks (can be overridden). */
    public static final int DEFAULT_MAX_CHUNKS = 1024;

    /** Error message for non-thread-safe context usage. */
    public static final String THREAD_SAFETY_WARNING = "This class is NOT thread-safe. It is designed to be used within a single-threaded event loop.";

    // Bitwise Constants for Chunk Management

    /** Number of bits in a long (64). */
    public static final int BITS_PER_LONG = 64;

    /** Shift amount for division by 64 (2^6 = 64). */
    public static final int DIV_64_SHIFT = 6;

    /** Mask for modulo 64 operations (64 - 1). */
    public static final int MOD_64_MASK = 63;

    /** Represents a fully occupied bitmap word (all bits set to 1). */
    public static final long ALL_ONES = -1L;

    /** Represents an unallocated state marker. */
    public static final long UNALLOCATED = -1L;

    // PageMap (Radix Tree) Configuration

    /** Number of bits for Level 1 index. */
    public static final int PAGE_MAP_L1_BITS = 12;

    /** Number of bits for Level 2 index. */
    public static final int PAGE_MAP_L2_BITS = 12;

    /**
     * Total shift to convert byte address to page number.
     * Must be consistent with Chunk Size if we want Chunks to align with Pages
     * conceptually,
     * though primarily this determines the granularity of the PageMap.
     */
    public static final int PAGE_SHIFT = PAGE_MAP_L1_BITS + PAGE_MAP_L2_BITS; // 24
}
