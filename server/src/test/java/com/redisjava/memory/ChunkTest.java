package com.redisjava.memory;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
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
        assertEquals(expectedSlots, chunk.getFreeCount());
        assertEquals(SLOT_SIZE, chunk.getSlotSize());
        assertTrue(chunk.isEmpty(), "başlangıçta boş");
        assertFalse(chunk.isFull(), "başlangıçta dolu değil");
    }

    // ── alloc / free ─────────────────────────────────────────────────────
    @Test

    public void testAllocAndFree_uniqueAlignedAddresses() {
        long addr1 = chunk.alloc();
        long addr2 = chunk.alloc();

        assertTrue(addr1 != -1, "addr1 geçerli");
        assertTrue(addr2 != -1, "addr2 geçerli");
        assertTrue(addr1 != addr2, "farklı adresler");

        // Adresler SLOT_SIZE'a hizalı mı?
        long base = chunk.getBaseAddress() + MemoryConstants.CHUNK_HEADER_SIZE;
        assertEquals(0L, (addr1 - base) % SLOT_SIZE);
        assertEquals(0L, (addr2 - base) % SLOT_SIZE);

        assertTrue(chunk.free(addr1), "free addr1");
        assertTrue(chunk.free(addr2), "free addr2");
        assertTrue(chunk.isEmpty(), "free sonrası boş");
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
            assertTrue(e.getMessage().contains("Double-free"), "Double-free mesajı");
        }
        assertTrue(threw, "double-free exception fırlattı");
    }

    // ── Geçersiz adres ───────────────────────────────────────────────────
    @Test

    public void testFree_invalidAddress_returnsFalse() {
        assertFalse(chunk.free(0), "0 adresi geçersiz");
        assertFalse(chunk.free(chunk.getBaseAddress() - 100), "bound dışı geçersiz");
    }

    // ── Next / Prev bağlantı listesi ─────────────────────────────────────
    @Test

    public void testNextPrevPointers() {
        Chunk c2 = new Chunk(32);
        try {
            chunk.setNext(c2);
            c2.setPrev(chunk);
            assertEquals(c2,    chunk.getNext());
            assertEquals(chunk, c2.getPrev());
        } finally {
            c2.destroy();
        }
    }
}
