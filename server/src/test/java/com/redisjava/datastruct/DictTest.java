package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.memory.MemoryManager;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Dict (Redis-style hash table with incremental rehashing).
 */
public class DictTest {

    private MemoryManager mem;
    private Dict dict;
    @BeforeEach

    public void setup() {
        mem = new MemoryManager(16);
        dict = new Dict(mem);
    }

    // ── Basic put / get ───────────────────────────────────────────────────

    /** put + get returns stored value */
    @Test
    public void testPut_get_returnsValue() {
        RedisObject val = RedisObject.string(new RString("hello".getBytes(), mem));
        dict.put("key1".getBytes(), val);
        RedisObject got = dict.get("key1".getBytes());
        assertNotNull(got);
        assertTrue(got.isString(), "is string");
    }

    /** get on missing key returns null */
    @Test
    public void testGet_missingKey_returnsNull() {
        RedisObject got = dict.get("ghost".getBytes());
        assertNull(got);
    }

    /** put overwrites existing key */
    @Test
    public void testPut_overwrite_returnsNewValue() {
        RedisObject v1 = RedisObject.string(new RString("first".getBytes(), mem));
        RedisObject v2 = RedisObject.string(new RString("second".getBytes(), mem));
        dict.put("k".getBytes(), v1);
        dict.put("k".getBytes(), v2);
        RedisObject got = dict.get("k".getBytes());
        assertNotNull(got);
        // v2 should be stored - getValue() returns the underlying RString
        assertEquals("second", new String(((RString) got.getValue()).getBytes()));
    }

    // ── size ─────────────────────────────────────────────────────────────

    /** size tracks entries correctly */
    @Test
    public void testSize_tracksEntries() {
        assertEquals(0, dict.size());
        dict.put("a".getBytes(), RedisObject.string(new RString("1".getBytes(), mem)));
        assertEquals(1, dict.size());
        dict.put("b".getBytes(), RedisObject.string(new RString("2".getBytes(), mem)));
        assertEquals(2, dict.size());
    }

    /** size does not double-count overwritten key */
    @Test
    public void testSize_overwrite_sameSize() {
        dict.put("x".getBytes(), RedisObject.string(new RString("a".getBytes(), mem)));
        dict.put("x".getBytes(), RedisObject.string(new RString("b".getBytes(), mem)));
        assertEquals(1, dict.size());
    }

    // ── remove ───────────────────────────────────────────────────────────

    /** remove existing key returns true, get returns null */
    @Test
    public void testRemove_existingKey_returnsTrue() {
        dict.put("rm".getBytes(), RedisObject.string(new RString("v".getBytes(), mem)));
        boolean removed = dict.remove("rm".getBytes());
        assertTrue(removed, "remove returns true");
        assertNull(dict.get("rm".getBytes()));
        assertEquals(0, dict.size());
    }

    /** remove missing key returns false */
    @Test
    public void testRemove_missingKey_returnsFalse() {
        boolean removed = dict.remove("no-such".getBytes());
        assertFalse(removed, "remove missing returns false");
    }

    // ── TTL / expiry ──────────────────────────────────────────────────────

    /** setExpireAt + ttlMs returns positive value */
    @Test
    public void testExpiry_setAndQuery() {
        dict.put("exp".getBytes(), RedisObject.string(new RString("v".getBytes(), mem)));
        long futureMs = System.currentTimeMillis() + 60_000;
        dict.setExpireAt("exp".getBytes(), futureMs);
        long ttl = dict.ttlMs("exp".getBytes(), System.currentTimeMillis());
        assertTrue(ttl > 0, "TTL > 0");
        assertTrue(ttl <= 60_000, "TTL <= 60000");
    }

    /** ttlMs on key with no expiry returns -1 */
    @Test
    public void testExpiry_noExpiry_returnsMinus1() {
        dict.put("noexp".getBytes(), RedisObject.string(new RString("v".getBytes(), mem)));
        long ttl = dict.ttlMs("noexp".getBytes(), System.currentTimeMillis());
        assertEquals(-1L, ttl);
    }

    /** clearExpire removes TTL, ttlMs returns -1 */
    @Test
    public void testExpiry_clearExpire() {
        dict.put("clr".getBytes(), RedisObject.string(new RString("v".getBytes(), mem)));
        dict.setExpireAt("clr".getBytes(), System.currentTimeMillis() + 60_000);
        dict.clearExpire("clr".getBytes());
        long ttl = dict.ttlMs("clr".getBytes(), System.currentTimeMillis());
        assertEquals(-1L, ttl);
    }

    // ── Iteration ─────────────────────────────────────────────────────────

    /** entries() iterates all stored keys */
    @Test
    public void testEntries_iteratesAll() {
        dict.put("e1".getBytes(), RedisObject.string(new RString("1".getBytes(), mem)));
        dict.put("e2".getBytes(), RedisObject.string(new RString("2".getBytes(), mem)));
        dict.put("e3".getBytes(), RedisObject.string(new RString("3".getBytes(), mem)));

        int count = 0;
        for (Dict.Entry e : dict.entries()) {
            assertNotNull(e.getKey());
            assertNotNull(e.getValue());
            count++;
        }
        assertEquals(3, count);
    }
}
