package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.memory.MemoryManager;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Db singleton (lifecycle, set/get, expiry, del, clear).
 */
public class DbTest {
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(16));
        Db.getInstance().clear();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Db.getInstance() after init() is non-null */
    @Test
    public void testInit_instanceNonNull() {
        assertNotNull(Db.getInstance());
    }

    /** Db.init() replaces existing instance */
    @Test
    public void testInit_replacesInstance() {
        Db first = Db.getInstance();
        Db.init(new MemoryManager(4));
        Db second = Db.getInstance();
        assertNotNull(second);
        // New instance is a fresh Db
        assertEquals(0, second.size());
    }

    // ── set / get (byte[]) ────────────────────────────────────────────────

    /** set + get round-trip on raw bytes */
    @Test
    public void testSet_get_rawBytes() {
        Db db = Db.getInstance();
        db.set("name".getBytes(), "Alice".getBytes());
        byte[] val = db.get("name".getBytes());
        assertNotNull(val);
        assertEquals("Alice", new String(val));
    }

    /** get on missing key returns null */
    @Test
    public void testGet_missingKey_returnsNull() {
        byte[] val = Db.getInstance().get("ghost".getBytes());
        assertNull(val);
    }

    /** set overwrites existing key */
    @Test
    public void testSet_overwrite() {
        Db db = Db.getInstance();
        db.set("k".getBytes(), "v1".getBytes());
        db.set("k".getBytes(), "v2".getBytes());
        assertEquals("v2", new String(db.get("k".getBytes())));
    }

    // ── del ───────────────────────────────────────────────────────────────

    /** del existing key returns true, get returns null */
    @Test
    public void testDel_existingKey() {
        Db db = Db.getInstance();
        db.set("d".getBytes(), "v".getBytes());
        boolean deleted = db.del("d".getBytes());
        assertTrue(deleted, "del returns true");
        assertNull(db.get("d".getBytes()));
    }

    /** del missing key returns false */
    @Test
    public void testDel_missingKey_returnsFalse() {
        assertFalse(Db.getInstance().del("no-key".getBytes()), "del missing returns false");
    }

    // ── size ─────────────────────────────────────────────────────────────

    /** size tracks stored entries */
    @Test
    public void testSize_tracks() {
        Db db = Db.getInstance();
        assertEquals(0, db.size());
        db.set("a".getBytes(), "1".getBytes());
        db.set("b".getBytes(), "2".getBytes());
        assertEquals(2, db.size());
    }

    // ── clear / flushAll ──────────────────────────────────────────────────

    /** clear() empties all keys */
    @Test
    public void testClear_emptiesDb() {
        Db db = Db.getInstance();
        db.set("x".getBytes(), "1".getBytes());
        db.set("y".getBytes(), "2".getBytes());
        db.clear();
        assertEquals(0, db.size());
        assertNull(db.get("x".getBytes()));
    }

    // ── TTL / expiry ──────────────────────────────────────────────────────

    /** expireAt + ttlSeconds returns positive value */
    @Test
    public void testExpiry_setAndQuery() {
        Db db = Db.getInstance();
        db.set("exp".getBytes(), "val".getBytes());
        long futureMs = System.currentTimeMillis() + 60_000;
        db.expireAt("exp".getBytes(), futureMs);
        long ttl = db.ttlSeconds("exp".getBytes());
        assertTrue(ttl > 0, "TTL > 0");
        assertTrue(ttl <= 60, "TTL <= 60");
    }

    /** persist() removes TTL, ttlSeconds returns -1 */
    @Test
    public void testPersist_removesTtl() {
        Db db = Db.getInstance();
        db.set("pk".getBytes(), "v".getBytes());
        db.expireAt("pk".getBytes(), System.currentTimeMillis() + 60_000);
        db.persist("pk".getBytes());
        long ttl = db.ttlSeconds("pk".getBytes());
        assertEquals(-1L, ttl);
    }
}
