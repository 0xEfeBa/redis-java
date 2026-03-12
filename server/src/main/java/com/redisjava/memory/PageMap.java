package com.redisjava.memory;

import com.redisjava.util.MemoryConstants;

/**
 * Radix Tree implementation for mapping memory addresses to Chunk IDs.
 * <p>
 * O(1) lookup time.
 * </p>
 * <p>
 * <b>WARNING:</b> {@value MemoryConstants#THREAD_SAFETY_WARNING}
 * </p>
 */
public class PageMap {

    public static final int NOT_FOUND = -1;

    // Configuration derived from MemoryConstants to ensure consistency
    private static final int L1_BITS = MemoryConstants.PAGE_MAP_L1_BITS;
    private static final int L2_BITS = MemoryConstants.PAGE_MAP_L2_BITS;
    private static final int PAGE_SHIFT = MemoryConstants.PAGE_SHIFT;

    private static final int L1_SIZE = 1 << L1_BITS;
    private static final int L2_SIZE = 1 << L2_BITS;

    private static final long L1_MASK = (L1_SIZE - 1);
    private static final long L2_MASK = (L2_SIZE - 1);

    private final int[][] l1Table;

    public PageMap() {
        this.l1Table = new int[L1_SIZE][];
    }

    public void put(long baseAddress, long size, int chunkId) {
        if (chunkId < 0) {
            throw new IllegalArgumentException("Invalid chunkId: " + chunkId);
        }

        long startPage = baseAddress >>> PAGE_SHIFT;
        long endPage = (baseAddress + size - 1) >>> PAGE_SHIFT;

        for (long page = startPage; page <= endPage; page++) {
            putPage(page, chunkId);
        }
    }

    private void putPage(long pageNumber, int chunkId) {
        int l1Index = (int) ((pageNumber >>> L2_BITS) & L1_MASK);
        int l2Index = (int) (pageNumber & L2_MASK);

        if (l1Table[l1Index] == null) {
            l1Table[l1Index] = new int[L2_SIZE];

            for (int i = 0; i < L2_SIZE; i++) {
                l1Table[l1Index][i] = NOT_FOUND;
            }
        }

        l1Table[l1Index][l2Index] = chunkId;
    }

    public int get(long address) {
        long pageNumber = address >>> PAGE_SHIFT;
        int l1Index = (int) ((pageNumber >>> L2_BITS) & L1_MASK);
        int l2Index = (int) (pageNumber & L2_MASK);

        int[] l2Table = l1Table[l1Index];
        if (l2Table == null) {
            return NOT_FOUND;
        }

        return l2Table[l2Index];
    }

    public void remove(long baseAddress, long size) {
        long startPage = baseAddress >>> PAGE_SHIFT;
        long endPage = (baseAddress + size - 1) >>> PAGE_SHIFT;

        for (long page = startPage; page <= endPage; page++) {
            removePage(page);
        }
    }

    private void removePage(long pageNumber) {
        int l1Index = (int) ((pageNumber >>> L2_BITS) & L1_MASK);
        int l2Index = (int) (pageNumber & L2_MASK);

        if (l1Table[l1Index] != null) {
            l1Table[l1Index][l2Index] = NOT_FOUND;
        }
    }

    /**
     * Clears all mappings.
     */
    public void clear() {
        for (int i = 0; i < L1_SIZE; i++) {
            l1Table[i] = null;
        }
    }
}
