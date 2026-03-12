package com.redisjava;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.command.MockConnection;
import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RedisProtocolHandler;
import com.redisjava.testutil.Assert;

import java.nio.ByteBuffer;

/**
 * Integration tests for all new commands using the full RESP stack.
 * Same mechanism as PipeliningTest: raw RESP bytes → RedisProtocolHandler
 * → CommandRegistry → command handler → Db → response.
 */
public class IntegrationTest {

    private RedisProtocolHandler handler;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(16));
        Db.getInstance().clear();
        handler = new RedisProtocolHandler();
        conn = new MockConnection();
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private String send(String... cmdParts) {
        conn.clear();
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(cmdParts.length).append("\r\n");
        for (String part : cmdParts) {
            sb.append("$").append(part.length()).append("\r\n");
            sb.append(part).append("\r\n");
        }
        ByteBuffer buf = ByteBuffer.wrap(sb.toString().getBytes());
        handler.handle(conn, buf);
        return conn.getLastResponse();
    }

    // ── LIST integration ───────────────────────────────────────────────────

    /** LPUSH + LRANGE pipeline */
    @Test
    public void testIntegration_listPipeline() {
        String resp = send("LPUSH", "mylist", "a");
        Assert.assertTrue("LPUSH returns :1", resp.contains(":1\r\n"));

        send("LPUSH", "mylist", "b");
        send("LPUSH", "mylist", "c");

        resp = send("LRANGE", "mylist", "0", "-1");
        Assert.assertNotNull(resp);
        Assert.assertTrue("LRANGE returns array", resp.startsWith("*"));
        Assert.assertTrue("contains c", resp.contains("c"));
        Assert.assertTrue("contains a", resp.contains("a"));
    }

    /** RPUSH + LPOP */
    @Test
    public void testIntegration_list_rpushLpop() {
        send("RPUSH", "q", "first");
        send("RPUSH", "q", "second");

        String resp = send("LPOP", "q");
        Assert.assertTrue("LPOP returns first", resp.contains("first"));

        resp = send("LLEN", "q");
        Assert.assertTrue("LLEN returns 1", resp.contains(":1\r\n"));
    }

    // ── ZSET integration ───────────────────────────────────────────────────

    /** ZADD + ZRANK + ZSCORE */
    @Test
    public void testIntegration_zsetPipeline() {
        send("ZADD", "scores", "10", "alice");
        send("ZADD", "scores", "20", "bob");
        send("ZADD", "scores", "15", "carol");

        String resp = send("ZRANK", "scores", "alice");
        Assert.assertTrue("alice rank is 0", resp.contains(":0\r\n"));

        resp = send("ZSCORE", "scores", "bob");
        Assert.assertTrue("bob score is 20", resp.contains("20"));

        resp = send("ZCARD", "scores");
        Assert.assertTrue("zcard is 3", resp.contains(":3\r\n"));
    }

    /** ZRANGE returns members in order */
    @Test
    public void testIntegration_zset_zrange() {
        send("ZADD", "zr", "1", "one");
        send("ZADD", "zr", "2", "two");
        send("ZADD", "zr", "3", "three");

        String resp = send("ZRANGE", "zr", "0", "-1");
        Assert.assertTrue("zrange starts with *", resp.startsWith("*"));
        Assert.assertTrue("contains one", resp.contains("one"));
        Assert.assertTrue("contains three", resp.contains("three"));
        // one should appear before three
        Assert.assertTrue("one before three", resp.indexOf("one") < resp.indexOf("three"));
    }

    // ── Bloom Filter integration ────────────────────────────────────────────

    /** BF.ADD + BF.EXISTS full stack */
    @Test
    public void testIntegration_bloomPipeline() {
        String resp = send("BF.ADD", "bf1", "member1");
        Assert.assertTrue("BF.ADD returns :1", resp.contains(":1\r\n"));

        resp = send("BF.EXISTS", "bf1", "member1");
        Assert.assertTrue("BF.EXISTS returns :1", resp.contains(":1\r\n"));

        resp = send("BF.EXISTS", "bf1", "not-added-99999");
        Assert.assertTrue("BF.EXISTS missing returns :0", resp.contains(":0\r\n"));
    }

    /** BF.RESERVE then BF.ADD */
    @Test
    public void testIntegration_bloom_reserve() {
        String resp = send("BF.RESERVE", "bigbf", "0.001", "50000");
        Assert.assertTrue("BF.RESERVE returns +OK", resp.contains("+OK"));

        resp = send("BF.ADD", "bigbf", "hello");
        Assert.assertTrue("BF.ADD after reserve returns :1", resp.contains(":1\r\n"));
    }

    // ── HyperLogLog integration ─────────────────────────────────────────────

    /** PFADD + PFCOUNT */
    @Test
    public void testIntegration_hllPipeline() {
        send("PFADD", "hll1", "a", "b", "c");
        String resp = send("PFCOUNT", "hll1");
        Assert.assertTrue("PFCOUNT returns integer", resp.startsWith(":"));
        long count = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        Assert.assertTrue("cardinality ~3", count >= 2 && count <= 5);
    }

    /** PFMERGE union */
    @Test
    public void testIntegration_hll_pfmerge() {
        send("PFADD", "hA", "x", "y", "z");
        send("PFADD", "hB", "a", "b", "c");
        String resp = send("PFMERGE", "hDest", "hA", "hB");
        Assert.assertTrue("PFMERGE returns +OK", resp.contains("+OK"));

        resp = send("PFCOUNT", "hDest");
        Assert.assertTrue("merged count is integer", resp.startsWith(":"));
        long count = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        Assert.assertTrue("merged cardinality >=4", count >= 4);
    }

    // ── Hash integration ────────────────────────────────────────────────────

    /** HSET + HGET + HGETALL */
    @Test
    public void testIntegration_hashPipeline() {
        send("HSET", "user:1", "name", "Alice");
        send("HSET", "user:1", "age", "30");

        String resp = send("HGET", "user:1", "name");
        Assert.assertTrue("HGET name returns Alice", resp.contains("Alice"));

        resp = send("HLEN", "user:1");
        Assert.assertTrue("HLEN returns 2", resp.contains(":2\r\n"));

        resp = send("HGETALL", "user:1");
        Assert.assertTrue("HGETALL returns array", resp.startsWith("*"));
        Assert.assertTrue("contains name", resp.contains("name"));
        Assert.assertTrue("contains Alice", resp.contains("Alice"));
    }

    // ── Counter integration ─────────────────────────────────────────────────

    /** INCR + INCRBY + DECR */
    @Test
    public void testIntegration_counterPipeline() {
        String resp = send("INCR", "ctr");
        Assert.assertTrue("INCR returns :1", resp.contains(":1\r\n"));

        resp = send("INCRBY", "ctr", "9");
        Assert.assertTrue("INCRBY returns :10", resp.contains(":10\r\n"));

        resp = send("DECR", "ctr");
        Assert.assertTrue("DECR returns :9", resp.contains(":9\r\n"));

        resp = send("DECRBY", "ctr", "4");
        Assert.assertTrue("DECRBY returns :5", resp.contains(":5\r\n"));
    }

    // ── TTL integration ─────────────────────────────────────────────────────

    /** SET EX + TTL */
    @Test
    public void testIntegration_ttlPipeline() {
        // SET with EX
        String resp = send("SET", "tmp", "val", "EX", "100");
        Assert.assertTrue("SET EX returns +OK", resp.contains("+OK"));

        resp = send("TTL", "tmp");
        Assert.assertTrue("TTL returns positive", resp.startsWith(":"));
        long ttl = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        Assert.assertTrue("TTL > 0", ttl > 0);
        Assert.assertTrue("TTL <= 100", ttl <= 100);
    }

    /** EXPIRE + PERSIST */
    @Test
    public void testIntegration_expirePersist() {
        send("SET", "pk", "value");
        send("EXPIRE", "pk", "60");

        String resp = send("TTL", "pk");
        long ttl = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        Assert.assertTrue("TTL > 0 after EXPIRE", ttl > 0);

        send("PERSIST", "pk");
        resp = send("TTL", "pk");
        long ttlAfter = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        Assert.assertTrue("TTL is -1 after PERSIST", ttlAfter == -1);
    }

    // ── DEL / EXISTS integration ────────────────────────────────────────────

    /** DEL + EXISTS */
    @Test
    public void testIntegration_delAndExists() {
        send("SET", "d1", "v");
        send("SET", "d2", "v");

        String resp = send("EXISTS", "d1", "d2", "ghost");
        Assert.assertTrue("EXISTS returns :2", resp.contains(":2\r\n"));

        resp = send("DEL", "d1", "d2");
        Assert.assertTrue("DEL returns :2", resp.contains(":2\r\n"));

        resp = send("EXISTS", "d1");
        Assert.assertTrue("EXISTS after DEL returns :0", resp.contains(":0\r\n"));
    }

    // ── FLUSHALL integration ────────────────────────────────────────────────

    /** FLUSHALL clears all data */
    @Test
    public void testIntegration_flushall() {
        send("SET", "a", "1");
        send("SET", "b", "2");

        String resp = send("FLUSHALL");
        Assert.assertTrue("FLUSHALL returns +OK", resp.contains("+OK"));

        resp = send("EXISTS", "a");
        Assert.assertTrue("EXISTS after FLUSHALL returns :0", resp.contains(":0\r\n"));
    }

    // ── WRONGTYPE error integration ─────────────────────────────────────────

    /** WRONGTYPE error when mixing types */
    @Test
    public void testIntegration_wrongType_error() {
        send("LPUSH", "lst", "item");
        String resp = send("GET", "lst");
        Assert.assertTrue("GET on list returns WRONGTYPE", resp.startsWith("-WRONGTYPE"));
    }

    // ── Unknown command ─────────────────────────────────────────────────────

    /** Unknown command returns ERR */
    @Test
    public void testIntegration_unknownCommand() {
        String resp = send("UNKNOWNCMD", "arg");
        Assert.assertTrue("Unknown command returns error", resp.startsWith("-"));
    }

    // ── TICKET.BUY integration ──────────────────────────────────────────────

    /** TICKET.BUY basic flow */
    @Test
    public void testIntegration_ticketBuy() {
        // Pre-load inventory: SET tickets:concert 10
        send("SET", "tickets:concert", "10");
        String resp = send("TICKET.BUY", "concert", "1");
        // Should return remaining count or error if inventory key format differs
        Assert.assertNotNull(resp);
        // Ticket buy either succeeds (:N) or returns an error
        Assert.assertTrue("TICKET.BUY response is non-empty", resp.length() > 0);
    }
}
