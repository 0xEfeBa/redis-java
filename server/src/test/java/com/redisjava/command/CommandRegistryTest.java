package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;

/**
 * Unit tests for CommandRegistry.lookup().
 * Verifies that every supported command resolves to a non-null Command,
 * and that unknown commands return null.
 */
public class CommandRegistryTest {

    private CommandRegistry registry;
    @BeforeEach

    public void setup() {
        registry = new CommandRegistry();
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Command lookup(String cmd) {
        byte[] bytes = cmd.getBytes();
        return registry.lookup(bytes, 0, bytes.length);
    }

    private void assertResolved(String cmd) {
        Assert.assertNotNull(lookup(cmd));
    }

    private void assertResolvedCI(String cmd) {
        // case-insensitive — try uppercase and lowercase
        byte[] upper = cmd.toUpperCase().getBytes();
        byte[] lower = cmd.toLowerCase().getBytes();
        Assert.assertNotNull(registry.lookup(upper, 0, upper.length));
        Assert.assertNotNull(registry.lookup(lower, 0, lower.length));
    }

    // ── PING / ECHO / INFO ────────────────────────────────────────────────
    @Test

    public void testLookup_PING() { assertResolved("PING"); }
    @Test
    public void testLookup_ECHO() { assertResolved("ECHO"); }
    @Test
    public void testLookup_INFO() { assertResolved("INFO"); }

    // ── String commands ───────────────────────────────────────────────────
    @Test

    public void testLookup_SET()    { assertResolved("SET"); }
    @Test
    public void testLookup_GET()    { assertResolved("GET"); }
    @Test
    public void testLookup_SETNX()  { assertResolved("SETNX"); }
    @Test
    public void testLookup_INCR()   { assertResolved("INCR"); }
    @Test
    public void testLookup_INCRBY() { assertResolved("INCRBY"); }
    @Test
    public void testLookup_DECR()   { assertResolved("DECR"); }
    @Test
    public void testLookup_DECRBY() { assertResolved("DECRBY"); }

    // ── Key-space commands ────────────────────────────────────────────────
    @Test

    public void testLookup_DEL()      { assertResolved("DEL"); }
    @Test
    public void testLookup_EXISTS()   { assertResolved("EXISTS"); }
    @Test
    public void testLookup_FLUSHALL() { assertResolved("FLUSHALL"); }
    @Test
    public void testLookup_EXPIRE()   { assertResolved("EXPIRE"); }
    @Test
    public void testLookup_TTL()      { assertResolved("TTL"); }
    @Test
    public void testLookup_PERSIST()  { assertResolved("PERSIST"); }

    // ── Hash commands ─────────────────────────────────────────────────────
    @Test

    public void testLookup_HSET()    { assertResolved("HSET"); }
    @Test
    public void testLookup_HGET()    { assertResolved("HGET"); }
    @Test
    public void testLookup_HDEL()    { assertResolved("HDEL"); }
    @Test
    public void testLookup_HEXISTS() { assertResolved("HEXISTS"); }
    @Test
    public void testLookup_HGETALL() { assertResolved("HGETALL"); }
    @Test
    public void testLookup_HLEN()    { assertResolved("HLEN"); }

    // ── List commands ─────────────────────────────────────────────────────
    @Test

    public void testLookup_LPUSH()  { assertResolved("LPUSH"); }
    @Test
    public void testLookup_RPUSH()  { assertResolved("RPUSH"); }
    @Test
    public void testLookup_LPOP()   { assertResolved("LPOP"); }
    @Test
    public void testLookup_RPOP()   { assertResolved("RPOP"); }
    @Test
    public void testLookup_LLEN()   { assertResolved("LLEN"); }
    @Test
    public void testLookup_LRANGE() { assertResolved("LRANGE"); }

    // ── Sorted set commands ───────────────────────────────────────────────
    @Test

    public void testLookup_ZADD()   { assertResolved("ZADD"); }
    @Test
    public void testLookup_ZREM()   { assertResolved("ZREM"); }
    @Test
    public void testLookup_ZRANK()  { assertResolved("ZRANK"); }
    @Test
    public void testLookup_ZRANGE() { assertResolved("ZRANGE"); }
    @Test
    public void testLookup_ZSCORE() { assertResolved("ZSCORE"); }
    @Test
    public void testLookup_ZCARD()  { assertResolved("ZCARD"); }

    // ── Pub/Sub commands ──────────────────────────────────────────────────
    @Test

    public void testLookup_SUBSCRIBE()   { assertResolved("SUBSCRIBE"); }
    @Test
    public void testLookup_UNSUBSCRIBE() { assertResolved("UNSUBSCRIBE"); }
    @Test
    public void testLookup_PUBLISH()     { assertResolved("PUBLISH"); }

    // ── Bloom Filter commands ─────────────────────────────────────────────
    @Test

    public void testLookup_BF_ADD()     { assertResolved("BF.ADD"); }
    @Test
    public void testLookup_BF_EXISTS()  { assertResolved("BF.EXISTS"); }
    @Test
    public void testLookup_BF_RESERVE() { assertResolved("BF.RESERVE"); }

    // ── HyperLogLog commands ──────────────────────────────────────────────
    @Test

    public void testLookup_PFADD()   { assertResolved("PFADD"); }
    @Test
    public void testLookup_PFCOUNT() { assertResolved("PFCOUNT"); }
    @Test
    public void testLookup_PFMERGE() { assertResolved("PFMERGE"); }

    // ── Custom command ────────────────────────────────────────────────────
    @Test

    public void testLookup_TICKET_BUY() { assertResolved("TICKET.BUY"); }

    // ── Case insensitive ──────────────────────────────────────────────────

    /** Commands resolve regardless of case */
    @Test
    public void testLookup_caseInsensitive_get()  { assertResolvedCI("get"); }
    @Test
    public void testLookup_caseInsensitive_set()  { assertResolvedCI("set"); }
    @Test
    public void testLookup_caseInsensitive_hset() { assertResolvedCI("hset"); }
    @Test
    public void testLookup_caseInsensitive_zadd() { assertResolvedCI("zadd"); }

    // ── Unknown commands return null ──────────────────────────────────────

    /** Unknown command returns null */
    @Test
    public void testLookup_unknown_returnsNull() {
        Assert.assertNull(lookup("NOTACOMMAND"));
    }

    /** Empty command returns null */
    @Test
    public void testLookup_empty_returnsNull() {
        byte[] empty = new byte[0];
        Assert.assertNull(registry.lookup(empty, 0, 0));
    }

    /** Partial command name returns null */
    @Test
    public void testLookup_partial_returnsNull() {
        Assert.assertNull(lookup("GE"));
        Assert.assertNull(lookup("SE"));
    }
}
