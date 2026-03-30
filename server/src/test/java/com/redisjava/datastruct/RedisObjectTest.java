package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.memory.MemoryManager;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedisObject type system.
 * Covers all Type enum values and their factory / type-check methods.
 */
public class RedisObjectTest {

    private MemoryManager mem;
    @BeforeEach

    public void setup() {
        mem = new MemoryManager(4);
    }

    // ── STRING ────────────────────────────────────────────────────────────

    /** RedisObject.string() → isString() true, other checks false */
    @Test
    public void testString_typeChecks() {
        RString rs = new RString("hello".getBytes(), mem);
        RedisObject obj = RedisObject.string(rs);
        assertTrue(obj.isString(), "isString");
        assertFalse(obj.isHash(), "not hash");
        assertFalse(obj.isList(), "not list");
        assertFalse(obj.isZset(), "not zset");
        assertFalse(obj.isBloom(), "not bloom");
        assertFalse(obj.isHll(), "not hll");
        assertEquals(RedisObject.Type.STRING, obj.getType());
    }

    // ── HASH ──────────────────────────────────────────────────────────────

    /** RedisObject.hash() → isHash() true, asHash() returns the Dict */
    @Test
    public void testHash_typeChecks() {
        Dict d = new Dict(mem);
        RedisObject obj = RedisObject.hash(d);
        assertTrue(obj.isHash(), "isHash");
        assertFalse(obj.isString(), "not string");
        assertEquals(RedisObject.Type.HASH, obj.getType());
        assertEquals(d, obj.asHash());
    }

    // ── LIST ──────────────────────────────────────────────────────────────

    /** RedisObject.list() → isList() true, asList() returns the RedisList */
    @Test
    public void testList_typeChecks() {
        RedisList list = new RedisList();
        RedisObject obj = RedisObject.list(list);
        assertTrue(obj.isList(), "isList");
        assertFalse(obj.isHash(), "not hash");
        assertEquals(RedisObject.Type.LIST, obj.getType());
        assertEquals(list, obj.asList());
    }

    // ── ZSET ──────────────────────────────────────────────────────────────

    /** RedisObject.zset() → isZset() true, asZset() returns SkipList */
    @Test
    public void testZset_typeChecks() {
        SkipList sl = new SkipList();
        RedisObject obj = RedisObject.zset(sl);
        assertTrue(obj.isZset(), "isZset");
        assertFalse(obj.isList(), "not list");
        assertEquals(RedisObject.Type.ZSET, obj.getType());
        assertEquals(sl, obj.asZset());
    }

    // ── BLOOM ─────────────────────────────────────────────────────────────

    /** RedisObject.bloom() → isBloom() true, asBloom() returns BloomFilter */
    @Test
    public void testBloom_typeChecks() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        RedisObject obj = RedisObject.bloom(bf);
        assertTrue(obj.isBloom(), "isBloom");
        assertFalse(obj.isString(), "not string");
        assertFalse(obj.isHll(), "not hll");
        assertEquals(RedisObject.Type.BLOOM, obj.getType());
        assertEquals(bf, obj.asBloom());
    }

    // ── HLL ───────────────────────────────────────────────────────────────

    /** RedisObject.hll() → isHll() true, asHll() returns HyperLogLog */
    @Test
    public void testHll_typeChecks() {
        HyperLogLog hll = new HyperLogLog();
        RedisObject obj = RedisObject.hll(hll);
        assertTrue(obj.isHll(), "isHll");
        assertFalse(obj.isBloom(), "not bloom");
        assertFalse(obj.isString(), "not string");
        assertEquals(RedisObject.Type.HLL, obj.getType());
        assertEquals(hll, obj.asHll());
    }

    // ── Wrong-type throws ─────────────────────────────────────────────────

    /** asHash() on non-hash throws IllegalStateException */
    @Test
    public void testAsHash_wrongType_throws() {
        RString rs = new RString("v".getBytes(), mem);
        RedisObject obj = RedisObject.string(rs);
        boolean threw = false;
        try {
            obj.asHash();
        } catch (IllegalStateException e) {
            threw = true;
        }
        assertTrue(threw, "asHash on string throws");
    }

    /** asHll() on non-hll throws IllegalStateException */
    @Test
    public void testAsHll_wrongType_throws() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        RedisObject obj = RedisObject.bloom(bf);
        boolean threw = false;
        try {
            obj.asHll();
        } catch (IllegalStateException e) {
            threw = true;
        }
        assertTrue(threw, "asHll on bloom throws");
    }

    /** asBloom() on non-bloom throws IllegalStateException */
    @Test
    public void testAsBloom_wrongType_throws() {
        HyperLogLog hll = new HyperLogLog();
        RedisObject obj = RedisObject.hll(hll);
        boolean threw = false;
        try {
            obj.asBloom();
        } catch (IllegalStateException e) {
            threw = true;
        }
        assertTrue(threw, "asBloom on hll throws");
    }
}
