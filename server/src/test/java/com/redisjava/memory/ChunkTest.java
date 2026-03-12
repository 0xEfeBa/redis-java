package com.redisjava.memory;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;
import com.redisjava.util.MemoryConstants;

/**
 * Chunk (16 MB off-heap bellek bloğu) unit testleri.
 */
public class ChunkTest {

    private static final int SLOT_SIZE = 64;
    private Chunk chunk;
    @BeforeEach

    public void setup() {
        chunk = new Chunk(SLOT_SIZE);
    }

    public void teardown() {
        if (chunk != null) chunk.destroy();
    }

    // ── Başlangıç durumu ─────────────────────────────────────────────────
    @Test

    public void testInitialization_allSlotsFree() {
        int expectedSlots = (MemoryConstants.CHUNK_SIZE - MemoryConstants.CHUNK_HEADER_SIZE) / SLOT_SIZE;
        Assert.assertEquals(expectedSlots, chunk.getFreeCount());
        Assert.assertEquals(SLOT_SIZE, chunk.getSlotSize());
        Assert.assertTrue("başlangıçta boş", chunk.isEmpty());
        Assert.assertFalse("başlangıçta dolu değil", chunk.isFull());
    }

    // ── alloc / free ─────────────────────────────────────────────────────
    @Test

    public void testAllocAndFree_uniqueAlignedAddresses() {
        long addr1 = chunk.alloc();
        long addr2 = chunk.alloc();

        Assert.assertTrue("addr1 geçerli", addr1 != -1);
        Assert.assertTrue("addr2 geçerli", addr2 != -1);
        Assert.assertTrue("farklı adresler", addr1 != addr2);

        // Adresler SLOT_SIZE'a hizalı mı?
        long base = chunk.getBaseAddress() + MemoryConstants.CHUNK_HEADER_SIZE;
        Assert.assertEquals(0L, (addr1 - base) % SLOT_SIZE);
        Assert.assertEquals(0L, (addr2 - base) % SLOT_SIZE);

        Assert.assertTrue("free addr1", chunk.free(addr1));
        Assert.assertTrue("free addr2", chunk.free(addr2));
        Assert.assertTrue("free sonrası boş", chunk.isEmpty());
    }

    // ── Double-free koruması ─────────────────────────────────────────────
    @Test

    public void testDoubleFree_throwsIllegalState() {
        long addr = chunk.alloc();
        chunk.free(addr);

        boolean threw = false;
        try {
            chunk.free(addr);
        } catch (IllegalStateException e) {
            threw = true;
            Assert.assertTrue("Double-free mesajı", e.getMessage().contains("Double-free"));
        }
        Assert.assertTrue("double-free exception fırlattı", threw);
    }

    // ── Geçersiz adres ───────────────────────────────────────────────────
    @Test

    public void testFree_invalidAddress_returnsFalse() {
        Assert.assertFalse("0 adresi geçersiz", chunk.free(0));
        Assert.assertFalse("bound dışı geçersiz", chunk.free(chunk.getBaseAddress() - 100));
    }

    // ── Next / Prev bağlantı listesi ─────────────────────────────────────
    @Test

    public void testNextPrevPointers() {
        Chunk c2 = new Chunk(32);
        try {
            chunk.setNext(c2);
            c2.setPrev(chunk);
            Assert.assertEquals(c2,    chunk.getNext());
            Assert.assertEquals(chunk, c2.getPrev());
        } finally {
            c2.destroy();
        }
    }
}
