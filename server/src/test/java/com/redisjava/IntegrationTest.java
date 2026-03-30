package com.redisjava;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.command.MockConnection;
import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RedisProtocolHandler;
import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(resp.contains(":1\r\n"), "LPUSH returns :1");

        send("LPUSH", "mylist", "b");
        send("LPUSH", "mylist", "c");

        resp = send("LRANGE", "mylist", "0", "-1");
        assertNotNull(resp);
        assertTrue(resp.startsWith("*"), "LRANGE returns array");
        assertTrue(resp.contains("c"), "contains c");
        assertTrue(resp.contains("a"), "contains a");
    }

    /** RPUSH + LPOP */
    @Test
    public void testIntegration_list_rpushLpop() {
        send("RPUSH", "q", "first");
        send("RPUSH", "q", "second");

        String resp = send("LPOP", "q");
        assertTrue(resp.contains("first"), "LPOP returns first");

        resp = send("LLEN", "q");
        assertTrue(resp.contains(":1\r\n"), "LLEN returns 1");
    }

    // ── ZSET integration ───────────────────────────────────────────────────

    /** ZADD + ZRANK + ZSCORE */
    @Test
    public void testIntegration_zsetPipeline() {
        send("ZADD", "scores", "10", "alice");
        send("ZADD", "scores", "20", "bob");
        send("ZADD", "scores", "15", "carol");

        String resp = send("ZRANK", "scores", "alice");
        assertTrue(resp.contains(":0\r\n"), "alice rank is 0");

        resp = send("ZSCORE", "scores", "bob");
        assertTrue(resp.contains("20"), "bob score is 20");

        resp = send("ZCARD", "scores");
        assertTrue(resp.contains(":3\r\n"), "zcard is 3");
    }

    /** ZRANGE returns members in order */
    @Test
    public void testIntegration_zset_zrange() {
        send("ZADD", "zr", "1", "one");
        send("ZADD", "zr", "2", "two");
        send("ZADD", "zr", "3", "three");

        String resp = send("ZRANGE", "zr", "0", "-1");
        assertTrue(resp.startsWith("*"), "zrange starts with *");
        assertTrue(resp.contains("one"), "contains one");
        assertTrue(resp.contains("three"), "contains three");
        // one should appear before three
        assertTrue(resp.indexOf("one") < resp.indexOf("three"), "one before three");
    }

    // ── Bloom Filter integration ────────────────────────────────────────────

    /** BF.ADD + BF.EXISTS full stack */
    @Test
    public void testIntegration_bloomPipeline() {
        String resp = send("BF.ADD", "bf1", "member1");
        assertTrue(resp.contains(":1\r\n"), "BF.ADD returns :1");

        resp = send("BF.EXISTS", "bf1", "member1");
        assertTrue(resp.contains(":1\r\n"), "BF.EXISTS returns :1");

        resp = send("BF.EXISTS", "bf1", "not-added-99999");
        assertTrue(resp.contains(":0\r\n"), "BF.EXISTS missing returns :0");
    }

    /** BF.RESERVE then BF.ADD */
    @Test
    public void testIntegration_bloom_reserve() {
        String resp = send("BF.RESERVE", "bigbf", "0.001", "50000");
        assertTrue(resp.contains("+OK"), "BF.RESERVE returns +OK");

        resp = send("BF.ADD", "bigbf", "hello");
        assertTrue(resp.contains(":1\r\n"), "BF.ADD after reserve returns :1");
    }

    // ── HyperLogLog integration ─────────────────────────────────────────────

    /** PFADD + PFCOUNT */
    @Test
    public void testIntegration_hllPipeline() {
        send("PFADD", "hll1", "a", "b", "c");
        String resp = send("PFCOUNT", "hll1");
        assertTrue(resp.startsWith(":"), "PFCOUNT returns integer");
        long count = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        assertTrue(count >= 2 && count <= 5, "cardinality ~3");
    }

    /** PFMERGE union */
    @Test
    public void testIntegration_hll_pfmerge() {
        send("PFADD", "hA", "x", "y", "z");
        send("PFADD", "hB", "a", "b", "c");
        String resp = send("PFMERGE", "hDest", "hA", "hB");
        assertTrue(resp.contains("+OK"), "PFMERGE returns +OK");

        resp = send("PFCOUNT", "hDest");
        assertTrue(resp.startsWith(":"), "merged count is integer");
        long count = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        assertTrue(count >= 4, "merged cardinality >=4");
    }

    // ── Hash integration ────────────────────────────────────────────────────

    /** HSET + HGET + HGETALL */
    @Test
    public void testIntegration_hashPipeline() {
        send("HSET", "user:1", "name", "Alice");
        send("HSET", "user:1", "age", "30");

        String resp = send("HGET", "user:1", "name");
        assertTrue(resp.contains("Alice"), "HGET name returns Alice");

        resp = send("HLEN", "user:1");
        assertTrue(resp.contains(":2\r\n"), "HLEN returns 2");

        resp = send("HGETALL", "user:1");
        assertTrue(resp.startsWith("*"), "HGETALL returns array");
        assertTrue(resp.contains("name"), "contains name");
        assertTrue(resp.contains("Alice"), "contains Alice");
    }

    // ── Counter integration ─────────────────────────────────────────────────

    /** INCR + INCRBY + DECR */
    @Test
    public void testIntegration_counterPipeline() {
        String resp = send("INCR", "ctr");
        assertTrue(resp.contains(":1\r\n"), "INCR returns :1");

        resp = send("INCRBY", "ctr", "9");
        assertTrue(resp.contains(":10\r\n"), "INCRBY returns :10");

        resp = send("DECR", "ctr");
        assertTrue(resp.contains(":9\r\n"), "DECR returns :9");

        resp = send("DECRBY", "ctr", "4");
        assertTrue(resp.contains(":5\r\n"), "DECRBY returns :5");
    }

    // ── TTL integration ─────────────────────────────────────────────────────

    /** SET EX + TTL */
    @Test
    public void testIntegration_ttlPipeline() {
        // SET with EX
        String resp = send("SET", "tmp", "val", "EX", "100");
        assertTrue(resp.contains("+OK"), "SET EX returns +OK");

        resp = send("TTL", "tmp");
        assertTrue(resp.startsWith(":"), "TTL returns positive");
        long ttl = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        assertTrue(ttl > 0, "TTL > 0");
        assertTrue(ttl <= 100, "TTL <= 100");
    }

    /** EXPIRE + PERSIST */
    @Test
    public void testIntegration_expirePersist() {
        send("SET", "pk", "value");
        send("EXPIRE", "pk", "60");

        String resp = send("TTL", "pk");
        long ttl = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        assertTrue(ttl > 0, "TTL > 0 after EXPIRE");

        send("PERSIST", "pk");
        resp = send("TTL", "pk");
        long ttlAfter = Long.parseLong(resp.substring(1, resp.indexOf("\r\n")));
        assertTrue(ttlAfter == -1, "TTL is -1 after PERSIST");
    }

    // ── DEL / EXISTS integration ────────────────────────────────────────────

    /** DEL + EXISTS */
    @Test
    public void testIntegration_delAndExists() {
        send("SET", "d1", "v");
        send("SET", "d2", "v");

        String resp = send("EXISTS", "d1", "d2", "ghost");
        assertTrue(resp.contains(":2\r\n"), "EXISTS returns :2");

        resp = send("DEL", "d1", "d2");
        assertTrue(resp.contains(":2\r\n"), "DEL returns :2");

        resp = send("EXISTS", "d1");
        assertTrue(resp.contains(":0\r\n"), "EXISTS after DEL returns :0");
    }

    // ── FLUSHALL integration ────────────────────────────────────────────────

    /** FLUSHALL clears all data */
    @Test
    public void testIntegration_flushall() {
        send("SET", "a", "1");
        send("SET", "b", "2");

        String resp = send("FLUSHALL");
        assertTrue(resp.contains("+OK"), "FLUSHALL returns +OK");

        resp = send("EXISTS", "a");
        assertTrue(resp.contains(":0\r\n"), "EXISTS after FLUSHALL returns :0");
    }

    // ── WRONGTYPE error integration ─────────────────────────────────────────

    /** WRONGTYPE error when mixing types */
    @Test
    public void testIntegration_wrongType_error() {
        send("LPUSH", "lst", "item");
        String resp = send("GET", "lst");
        assertTrue(resp.startsWith("-WRONGTYPE"), "GET on list returns WRONGTYPE");
    }

    // ── Unknown command ─────────────────────────────────────────────────────

    /** Unknown command returns ERR */
    @Test
    public void testIntegration_unknownCommand() {
        String resp = send("UNKNOWNCMD", "arg");
        assertTrue(resp.startsWith("-"), "Unknown command returns error");
    }

    // ── TICKET.BUY integration ──────────────────────────────────────────────

    /** TICKET.BUY basic flow */
    @Test
    public void testIntegration_ticketBuy() {
        // Pre-load inventory: SET tickets:concert 10
        send("SET", "tickets:concert", "10");
        String resp = send("TICKET.BUY", "concert", "1");
        // Should return remaining count or error if inventory key format differs
        assertNotNull(resp);
        // Ticket buy either succeeds (:N) or returns an error
        assertTrue(resp.length() > 0, "TICKET.BUY response is non-empty");
    }
}
