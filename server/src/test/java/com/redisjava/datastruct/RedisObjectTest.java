package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.memory.MemoryManager;
import com.redisjava.testutil.Assert;

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
        Assert.assertTrue("isString", obj.isString());
        Assert.assertFalse("not hash", obj.isHash());
        Assert.assertFalse("not list", obj.isList());
        Assert.assertFalse("not zset", obj.isZset());
        Assert.assertFalse("not bloom", obj.isBloom());
        Assert.assertFalse("not hll", obj.isHll());
        Assert.assertEquals(RedisObject.Type.STRING, obj.getType());
    }

    // ── HASH ──────────────────────────────────────────────────────────────

    /** RedisObject.hash() → isHash() true, asHash() returns the Dict */
    @Test
    public void testHash_typeChecks() {
        Dict d = new Dict(mem);
        RedisObject obj = RedisObject.hash(d);
        Assert.assertTrue("isHash", obj.isHash());
        Assert.assertFalse("not string", obj.isString());
        Assert.assertEquals(RedisObject.Type.HASH, obj.getType());
        Assert.assertEquals(d, obj.asHash());
    }

    // ── LIST ──────────────────────────────────────────────────────────────

    /** RedisObject.list() → isList() true, asList() returns the RedisList */
    @Test
    public void testList_typeChecks() {
        RedisList list = new RedisList();
        RedisObject obj = RedisObject.list(list);
        Assert.assertTrue("isList", obj.isList());
        Assert.assertFalse("not hash", obj.isHash());
        Assert.assertEquals(RedisObject.Type.LIST, obj.getType());
        Assert.assertEquals(list, obj.asList());
    }

    // ── ZSET ──────────────────────────────────────────────────────────────

    /** RedisObject.zset() → isZset() true, asZset() returns SkipList */
    @Test
    public void testZset_typeChecks() {
        SkipList sl = new SkipList();
        RedisObject obj = RedisObject.zset(sl);
        Assert.assertTrue("isZset", obj.isZset());
        Assert.assertFalse("not list", obj.isList());
        Assert.assertEquals(RedisObject.Type.ZSET, obj.getType());
        Assert.assertEquals(sl, obj.asZset());
    }

    // ── BLOOM ─────────────────────────────────────────────────────────────

    /** RedisObject.bloom() → isBloom() true, asBloom() returns BloomFilter */
    @Test
    public void testBloom_typeChecks() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        RedisObject obj = RedisObject.bloom(bf);
        Assert.assertTrue("isBloom", obj.isBloom());
        Assert.assertFalse("not string", obj.isString());
        Assert.assertFalse("not hll", obj.isHll());
        Assert.assertEquals(RedisObject.Type.BLOOM, obj.getType());
        Assert.assertEquals(bf, obj.asBloom());
    }

    // ── HLL ───────────────────────────────────────────────────────────────

    /** RedisObject.hll() → isHll() true, asHll() returns HyperLogLog */
    @Test
    public void testHll_typeChecks() {
        HyperLogLog hll = new HyperLogLog();
        RedisObject obj = RedisObject.hll(hll);
        Assert.assertTrue("isHll", obj.isHll());
        Assert.assertFalse("not bloom", obj.isBloom());
        Assert.assertFalse("not string", obj.isString());
        Assert.assertEquals(RedisObject.Type.HLL, obj.getType());
        Assert.assertEquals(hll, obj.asHll());
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
        Assert.assertTrue("asHash on string throws", threw);
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
        Assert.assertTrue("asHll on bloom throws", threw);
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
        Assert.assertTrue("asBloom on hll throws", threw);
    }
}
