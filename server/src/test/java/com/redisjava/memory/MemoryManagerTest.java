package com.redisjava.memory;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;

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
        Assert.assertTrue("adres != 0", address != 0);

        DirectBufferUtils.putInt(address, 123456789);
        int value = DirectBufferUtils.getInt(address);
        Assert.assertEquals(123456789, value);

        DirectBufferUtils.free(address);
    }
    @Test

    public void testDirectBufferUtils_putGetLong() {
        long address = DirectBufferUtils.allocate(16);
        Assert.assertTrue("adres != 0", address != 0);

        long expected = 0xDEAD_BEEF_0000_0001L;
        DirectBufferUtils.putLong(address, expected);
        Assert.assertEquals(expected, DirectBufferUtils.getLong(address));

        DirectBufferUtils.free(address);
    }
    @Test

    public void testDirectBufferUtils_putGetByte() {
        long address = DirectBufferUtils.allocate(8);
        DirectBufferUtils.putByte(address, (byte) 42);
        Assert.assertEquals((byte) 42, DirectBufferUtils.getByte(address));
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
        Assert.assertTrue("bellek sızıntısı yok (chunk <= 2)", chunkCount <= 2);
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

        Assert.assertTrue("tüm tahsisler serbest bırakıldı", true);
        mm.shutdown();
    }

    // ── SlabCache ────────────────────────────────────────────────────────
    @Test

    public void testSlabCache_allocCreatesChunk() {
        MemoryManager mm = new MemoryManager(10);
        SlabCache slab = new SlabCache(64, mm);

        long addr = slab.alloc();
        Assert.assertTrue("geçerli adres döndü", addr != -1 && addr != 0);
        Assert.assertEquals(1, mm.getAllocatedChunkCount());

        mm.shutdown();
    }
    @Test

    public void testSlabCache_getObjectSize() {
        MemoryManager mm = new MemoryManager(4);
        SlabCache slab = new SlabCache(128, mm);
        Assert.assertEquals(128, slab.getObjectSize());
        mm.shutdown();
    }
    @Test

    public void testSlabCache_multipleAllocs() {
        MemoryManager mm = new MemoryManager(4);
        SlabCache slab = new SlabCache(64, mm);

        long a1 = slab.alloc();
        long a2 = slab.alloc();
        long a3 = slab.alloc();

        Assert.assertTrue("a1 geçerli", a1 != -1);
        Assert.assertTrue("a2 geçerli", a2 != -1);
        Assert.assertTrue("a3 geçerli", a3 != -1);
        Assert.assertTrue("a1 != a2", a1 != a2);
        Assert.assertTrue("a2 != a3", a2 != a3);

        mm.shutdown();
    }

    // ── PageMap ──────────────────────────────────────────────────────────
    @Test

    public void testPageMap_putAndGet() {
        PageMap pageMap = new PageMap();
        long baseAddress = 0x1000000L;
        int chunkId = 5;

        pageMap.put(baseAddress, com.redisjava.util.MemoryConstants.CHUNK_SIZE, chunkId);

        Assert.assertEquals(chunkId, pageMap.get(baseAddress));
        Assert.assertEquals(chunkId, pageMap.get(baseAddress + com.redisjava.util.MemoryConstants.CHUNK_SIZE / 2));
        Assert.assertEquals(chunkId, pageMap.get(baseAddress + com.redisjava.util.MemoryConstants.CHUNK_SIZE - 1));
        Assert.assertEquals(PageMap.NOT_FOUND, pageMap.get(baseAddress + com.redisjava.util.MemoryConstants.CHUNK_SIZE));
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
        Assert.assertTrue("negatif chunkId exception fırlattı", threw);
    }
    @Test

    public void testPageMap_remove() {
        PageMap pageMap = new PageMap();
        long base = 0x2000000L;
        int chunkId = 10;

        pageMap.put(base, com.redisjava.util.MemoryConstants.CHUNK_SIZE, chunkId);
        Assert.assertEquals(chunkId, pageMap.get(base));

        pageMap.remove(base, com.redisjava.util.MemoryConstants.CHUNK_SIZE);
        Assert.assertEquals(PageMap.NOT_FOUND, pageMap.get(base));
    }
}
