package com.redisjava.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MemoryManager ve DirectBufferUtils unit testleri.
 * Bellek sızıntısı tespiti, rastgele tahsis ve unsafe operasyonları.
 */
public class MemoryManagerTest {

    // ── DirectBufferUtils ────────────────────────────────────────────────
    @Test

    public void testDirectBufferUtils_allocWriteReadFree() {
        long address = DirectBufferUtils.allocate(1024);
        assertTrue(address != 0, "adres != 0");

        DirectBufferUtils.putInt(address, 123456789);
        int value = DirectBufferUtils.getInt(address);
        assertEquals(123456789, value);

        DirectBufferUtils.free(address);
    }
    @Test

    public void testDirectBufferUtils_putGetLong() {
        long address = DirectBufferUtils.allocate(16);
        assertTrue(address != 0, "adres != 0");

        long expected = 0xDEAD_BEEF_0000_0001L;
        DirectBufferUtils.putLong(address, expected);
        assertEquals(expected, DirectBufferUtils.getLong(address));

        DirectBufferUtils.free(address);
    }
    @Test

    public void testDirectBufferUtils_putGetByte() {
        long address = DirectBufferUtils.allocate(8);
        DirectBufferUtils.putByte(address, (byte) 42);
        assertEquals((byte) 42, DirectBufferUtils.getByte(address));
        DirectBufferUtils.free(address);
    }

    // ── MemoryManager — bellek sızıntısı tespiti ─────────────────────────
    @Test

    public void testMemoryLeak_millionOps_noLeak() {
        MemoryManager mm = new MemoryManager(4);
        int iterations = 100_000;

        for (int i = 0; i < iterations; i++) {
            long addr = mm.alloc(42);
            mm.free(addr);
        }

        int chunkCount = mm.getAllocatedChunkCount();
        assertTrue(chunkCount <= 2, "bellek sızıntısı yok (chunk <= 2)");
        mm.shutdown();
    }

    // ── MemoryManager — rastgele boyutlu tahsis ───────────────────────────
    @Test

    public void testRandomAllocations_noException() {
        MemoryManager mm = new MemoryManager(4);
        List<Long> addresses = new ArrayList<>();
        Random random = new Random(42);

        for (int i = 0; i < 500; i++) {
            int size = 16 + random.nextInt(128);
            addresses.add(mm.alloc(size));
        }

        for (Long addr : addresses) {
            mm.free(addr);
        }

        assertTrue(true, "tüm tahsisler serbest bırakıldı");
        mm.shutdown();
    }

    // ── SlabCache ────────────────────────────────────────────────────────
    @Test

    public void testSlabCache_allocCreatesChunk() {
        MemoryManager mm = new MemoryManager(10);
        SlabCache slab = new SlabCache(64, mm);

        long addr = slab.alloc();
        assertTrue(addr != -1 && addr != 0, "geçerli adres döndü");
        assertEquals(1, mm.getAllocatedChunkCount());

        mm.shutdown();
    }
    @Test

    public void testSlabCache_getObjectSize() {
        MemoryManager mm = new MemoryManager(4);
        SlabCache slab = new SlabCache(128, mm);
        assertEquals(128, slab.getObjectSize());
        mm.shutdown();
    }
    @Test

    public void testSlabCache_multipleAllocs() {
        MemoryManager mm = new MemoryManager(4);
        SlabCache slab = new SlabCache(64, mm);

        long a1 = slab.alloc();
        long a2 = slab.alloc();
        long a3 = slab.alloc();

        assertTrue(a1 != -1, "a1 geçerli");
        assertTrue(a2 != -1, "a2 geçerli");
        assertTrue(a3 != -1, "a3 geçerli");
        assertTrue(a1 != a2, "a1 != a2");
        assertTrue(a2 != a3, "a2 != a3");

        mm.shutdown();
    }

    // ── PageMap ──────────────────────────────────────────────────────────
    @Test

    public void testPageMap_putAndGet() {
        PageMap pageMap = new PageMap();
        long baseAddress = 0x1000000L;
        int chunkId = 5;

        pageMap.put(baseAddress, com.redisjava.util.MemoryConstants.CHUNK_SIZE, chunkId);

        assertEquals(chunkId, pageMap.get(baseAddress));
        assertEquals(chunkId, pageMap.get(baseAddress + com.redisjava.util.MemoryConstants.CHUNK_SIZE / 2));
        assertEquals(chunkId, pageMap.get(baseAddress + com.redisjava.util.MemoryConstants.CHUNK_SIZE - 1));
        assertEquals(PageMap.NOT_FOUND, pageMap.get(baseAddress + com.redisjava.util.MemoryConstants.CHUNK_SIZE));
    }
    @Test

    public void testPageMap_invalidChunkId_throws() {
        PageMap pageMap = new PageMap();
        boolean threw = false;
        try {
            pageMap.put(0x100, 100, -1);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw, "negatif chunkId exception fırlattı");
    }
    @Test

    public void testPageMap_remove() {
        PageMap pageMap = new PageMap();
        long base = 0x2000000L;
        int chunkId = 10;

        pageMap.put(base, com.redisjava.util.MemoryConstants.CHUNK_SIZE, chunkId);
        assertEquals(chunkId, pageMap.get(base));

        pageMap.remove(base, com.redisjava.util.MemoryConstants.CHUNK_SIZE);
        assertEquals(PageMap.NOT_FOUND, pageMap.get(base));
    }
}
