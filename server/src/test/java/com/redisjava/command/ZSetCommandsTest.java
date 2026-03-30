package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RedisProtocolHandler;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

/**
 * Integration tests for ZSET commands: ZADD, ZREM, ZRANK, ZRANGE, ZSCORE, ZCARD.
 */
public class ZSetCommandsTest {

    private RedisProtocolHandler handler;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(2));
        handler = new RedisProtocolHandler();
        conn = new MockConnection();
    }

    private static String cmd(String... parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(parts.length).append("\r\n");
        for (String p : parts) {
            sb.append("$").append(p.length()).append("\r\n").append(p).append("\r\n");
        }
        return sb.toString();
    }

    private String exec(String... parts) {
        conn.clear();
        handler.handle(conn, ByteBuffer.wrap(cmd(parts).getBytes()));
        return conn.getLastResponse();
    }

    // ── ZADD ──────────────────────────────────────────────────────────────
    @Test

    public void testZadd_newElement_returns1() {
        assertEquals(":1\r\n", exec("ZADD", "myzset", "1", "a"));
    }
    @Test

    public void testZadd_existingElement_returns0() {
        exec("ZADD", "myzset", "1", "a");
        assertEquals(":0\r\n", exec("ZADD", "myzset", "2", "a"));
    }
    @Test

    public void testZadd_multipleElements() {
        // 3 new elements → returns 3
        assertEquals(":3\r\n", exec("ZADD", "myzset", "1", "a", "2", "b", "3", "c"));
    }

    // ── ZCARD ─────────────────────────────────────────────────────────────
    @Test

    public void testZcard_emptyKey_returns0() {
        assertEquals(":0\r\n", exec("ZCARD", "nosuchkey"));
    }
    @Test

    public void testZcard_afterAdds() {
        exec("ZADD", "myzset", "1", "a", "2", "b", "3", "c");
        assertEquals(":3\r\n", exec("ZCARD", "myzset"));
    }

    // ── ZSCORE ────────────────────────────────────────────────────────────
    @Test

    public void testZscore_missingKey_returnsNull() {
        assertEquals("$-1\r\n", exec("ZSCORE", "nosuchkey", "a"));
    }
    @Test

    public void testZscore_missingMember_returnsNull() {
        exec("ZADD", "myzset", "1", "a");
        assertEquals("$-1\r\n", exec("ZSCORE", "myzset", "x"));
    }
    @Test

    public void testZscore_returnsCorrectScore() {
        exec("ZADD", "myzset", "42", "a");
        String resp = exec("ZSCORE", "myzset", "a");
        assertTrue(resp.contains("42"), "Score should be 42");
    }

    // ── ZRANK ─────────────────────────────────────────────────────────────
    @Test

    public void testZrank_missingMember_returnsNull() {
        assertEquals("$-1\r\n", exec("ZRANK", "myzset", "x"));
    }
    @Test

    public void testZrank_ascendingOrder() {
        exec("ZADD", "myzset", "3", "c", "1", "a", "2", "b");
        assertEquals(":0\r\n", exec("ZRANK", "myzset", "a"));
        assertEquals(":1\r\n", exec("ZRANK", "myzset", "b"));
        assertEquals(":2\r\n", exec("ZRANK", "myzset", "c"));
    }

    // ── ZRANGE ────────────────────────────────────────────────────────────
    @Test

    public void testZrange_fullRange() {
        exec("ZADD", "myzset", "1", "a", "2", "b", "3", "c");
        String resp = exec("ZRANGE", "myzset", "0", "-1");
        assertTrue(resp.contains("$1\r\na\r\n"), "contains a");
        assertTrue(resp.contains("$1\r\nb\r\n"), "contains b");
        assertTrue(resp.contains("$1\r\nc\r\n"), "contains c");
    }
    @Test

    public void testZrange_withScores() {
        exec("ZADD", "myzset", "1", "a");
        String resp = exec("ZRANGE", "myzset", "0", "-1", "WITHSCORES");
        assertTrue(resp.contains("$1\r\na\r\n"), "contains member");
        assertTrue(resp.contains("$1\r\n1\r\n"), "contains score");
    }
    @Test

    public void testZrange_emptyKey_returnsEmptyArray() {
        assertEquals("*0\r\n", exec("ZRANGE", "nosuchkey", "0", "-1"));
    }

    // ── ZREM ──────────────────────────────────────────────────────────────
    @Test

    public void testZrem_existingMember_returns1() {
        exec("ZADD", "myzset", "1", "a");
        assertEquals(":1\r\n", exec("ZREM", "myzset", "a"));
    }
    @Test

    public void testZrem_missingMember_returns0() {
        assertEquals(":0\r\n", exec("ZREM", "myzset", "x"));
    }
    @Test

    public void testZrem_removesKeyWhenEmpty() {
        exec("ZADD", "myzset", "1", "only");
        exec("ZREM", "myzset", "only");
        assertEquals(":0\r\n", exec("ZCARD", "myzset"));
    }

    // ── Type safety ───────────────────────────────────────────────────────
    @Test

    public void testZset_wrongType_returnsError() {
        exec("SET", "strkey", "value");
        String resp = exec("ZADD", "strkey", "1", "member");
        assertTrue(resp.startsWith("-WRONGTYPE"), "Should return WRONGTYPE error");
    }
}
