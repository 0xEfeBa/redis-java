package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

/**
 * Unit tests for SkipList (ZSET core data structure).
 */
public class SkipListTest {

    private SkipList zset;
    @BeforeEach

    public void setup() {
        zset = new SkipList();
    }

    // ── ZADD ──────────────────────────────────────────────────────────────
    @Test

    public void testZadd_newElement_returns1() {
        assertEquals(1, zset.zadd(1.0, "a".getBytes()));
    }
    @Test

    public void testZadd_existingElement_returns0() {
        zset.zadd(1.0, "a".getBytes());
        assertEquals(0, zset.zadd(2.0, "a".getBytes()));
    }
    @Test

    public void testZadd_updatesScore() {
        zset.zadd(1.0, "a".getBytes());
        zset.zadd(5.0, "a".getBytes());
        assertEquals(5.0, zset.zscore("a".getBytes()), 0.001);
    }

    // ── ZCARD ─────────────────────────────────────────────────────────────
    @Test

    public void testZcard_empty_returns0() {
        assertEquals(0, zset.zcard());
    }
    @Test

    public void testZcard_afterAdds_returnsCorrectCount() {
        zset.zadd(1.0, "a".getBytes());
        zset.zadd(2.0, "b".getBytes());
        zset.zadd(3.0, "c".getBytes());
        assertEquals(3, zset.zcard());
    }

    // ── ZSCORE ────────────────────────────────────────────────────────────
    @Test

    public void testZscore_missingMember_returnsNaN() {
        assertTrue(Double.isNaN(zset.zscore("x".getBytes())), "Should be NaN");
    }
    @Test

    public void testZscore_returnsCorrectScore() {
        zset.zadd(42.5, "foo".getBytes());
        assertEquals(42.5, zset.zscore("foo".getBytes()), 0.001);
    }

    // ── ZRANK ─────────────────────────────────────────────────────────────
    @Test

    public void testZrank_missingMember_returnsMinus1() {
        assertEquals(-1, zset.zrank("x".getBytes()));
    }
    @Test

    public void testZrank_ascendingOrder() {
        zset.zadd(3.0, "c".getBytes());
        zset.zadd(1.0, "a".getBytes());
        zset.zadd(2.0, "b".getBytes());
        assertEquals(0, zset.zrank("a".getBytes()));
        assertEquals(1, zset.zrank("b".getBytes()));
        assertEquals(2, zset.zrank("c".getBytes()));
    }

    // ── ZRANGE ────────────────────────────────────────────────────────────
    @Test

    public void testZrange_fullRange() {
        zset.zadd(1.0, "a".getBytes());
        zset.zadd(2.0, "b".getBytes());
        zset.zadd(3.0, "c".getBytes());
        SkipList.ZEntry[] entries = zset.zrange(0, -1);
        assertEquals(3, entries.length);
        assertTrue(Arrays.equals("a".getBytes(), entries[0].member), "first should be a");
        assertTrue(Arrays.equals("c".getBytes(), entries[2].member), "last should be c");
    }
    @Test

    public void testZrange_subRange() {
        zset.zadd(1.0, "a".getBytes());
        zset.zadd(2.0, "b".getBytes());
        zset.zadd(3.0, "c".getBytes());
        SkipList.ZEntry[] entries = zset.zrange(1, 1);
        assertEquals(1, entries.length);
        assertTrue(Arrays.equals("b".getBytes(), entries[0].member), "should be b");
    }
    @Test

    public void testZrange_emptySet_returnsEmptyArray() {
        SkipList.ZEntry[] entries = zset.zrange(0, -1);
        assertEquals(0, entries.length);
    }

    // ── ZREM ──────────────────────────────────────────────────────────────
    @Test

    public void testZrem_existingMember_returnsTrue() {
        zset.zadd(1.0, "a".getBytes());
        assertTrue(zset.zrem("a".getBytes()), "zrem should return true");
    }
    @Test

    public void testZrem_missingMember_returnsFalse() {
        assertFalse(zset.zrem("x".getBytes()), "zrem should return false");
    }
    @Test

    public void testZrem_decreasesSize() {
        zset.zadd(1.0, "a".getBytes());
        zset.zadd(2.0, "b".getBytes());
        zset.zrem("a".getBytes());
        assertEquals(1, zset.zcard());
    }
    @Test

    public void testZrem_andReinsert_correctOrder() {
        zset.zadd(1.0, "a".getBytes());
        zset.zadd(2.0, "b".getBytes());
        zset.zadd(3.0, "c".getBytes());
        zset.zrem("b".getBytes());
        zset.zadd(4.0, "b".getBytes()); // b now has highest score
        SkipList.ZEntry[] entries = zset.zrange(0, -1);
        assertEquals(3, entries.length);
        assertTrue(Arrays.equals("b".getBytes(), entries[2].member), "last should be b");
    }

    // ── Large scale ───────────────────────────────────────────────────────
    @Test

    public void testZadd_1000elements_correctRanks() {
        for (int i = 0; i < 1000; i++) {
            zset.zadd(i, ("member" + i).getBytes());
        }
        assertEquals(1000, zset.zcard());
        assertEquals(0, zset.zrank("member0".getBytes()));
        assertEquals(999, zset.zrank("member999".getBytes()));
        assertEquals(500, zset.zrank("member500".getBytes()));
    }
}
