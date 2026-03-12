package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.memory.MemoryManager;
import com.redisjava.testutil.Assert;

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
        Assert.assertNotNull(Db.getInstance());
    }

    /** Db.init() replaces existing instance */
    @Test
    public void testInit_replacesInstance() {
        Db first = Db.getInstance();
        Db.init(new MemoryManager(4));
        Db second = Db.getInstance();
        Assert.assertNotNull(second);
        // New instance is a fresh Db
        Assert.assertEquals(0, second.size());
    }

    // ── set / get (byte[]) ────────────────────────────────────────────────

    /** set + get round-trip on raw bytes */
    @Test
    public void testSet_get_rawBytes() {
        Db db = Db.getInstance();
        db.set("name".getBytes(), "Alice".getBytes());
        byte[] val = db.get("name".getBytes());
        Assert.assertNotNull(val);
        Assert.assertEquals("Alice", new String(val));
    }

    /** get on missing key returns null */
    @Test
    public void testGet_missingKey_returnsNull() {
        byte[] val = Db.getInstance().get("ghost".getBytes());
        Assert.assertNull(val);
    }

    /** set overwrites existing key */
    @Test
    public void testSet_overwrite() {
        Db db = Db.getInstance();
        db.set("k".getBytes(), "v1".getBytes());
        db.set("k".getBytes(), "v2".getBytes());
        Assert.assertEquals("v2", new String(db.get("k".getBytes())));
    }

    // ── del ───────────────────────────────────────────────────────────────

    /** del existing key returns true, get returns null */
    @Test
    public void testDel_existingKey() {
        Db db = Db.getInstance();
        db.set("d".getBytes(), "v".getBytes());
        boolean deleted = db.del("d".getBytes());
        Assert.assertTrue("del returns true", deleted);
        Assert.assertNull(db.get("d".getBytes()));
    }

    /** del missing key returns false */
    @Test
    public void testDel_missingKey_returnsFalse() {
        Assert.assertFalse("del missing returns false", Db.getInstance().del("no-key".getBytes()));
    }

    // ── size ─────────────────────────────────────────────────────────────

    /** size tracks stored entries */
    @Test
    public void testSize_tracks() {
        Db db = Db.getInstance();
        Assert.assertEquals(0, db.size());
        db.set("a".getBytes(), "1".getBytes());
        db.set("b".getBytes(), "2".getBytes());
        Assert.assertEquals(2, db.size());
    }

    // ── clear / flushAll ──────────────────────────────────────────────────

    /** clear() empties all keys */
    @Test
    public void testClear_emptiesDb() {
        Db db = Db.getInstance();
        db.set("x".getBytes(), "1".getBytes());
        db.set("y".getBytes(), "2".getBytes());
        db.clear();
        Assert.assertEquals(0, db.size());
        Assert.assertNull(db.get("x".getBytes()));
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
        Assert.assertTrue("TTL > 0", ttl > 0);
        Assert.assertTrue("TTL <= 60", ttl <= 60);
    }

    /** persist() removes TTL, ttlSeconds returns -1 */
    @Test
    public void testPersist_removesTtl() {
        Db db = Db.getInstance();
        db.set("pk".getBytes(), "v".getBytes());
        db.expireAt("pk".getBytes(), System.currentTimeMillis() + 60_000);
        db.persist("pk".getBytes());
        long ttl = db.ttlSeconds("pk".getBytes());
        Assert.assertEquals(-1L, ttl);
    }
}
