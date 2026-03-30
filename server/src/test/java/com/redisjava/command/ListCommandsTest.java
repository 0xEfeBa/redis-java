package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RedisProtocolHandler;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

/**
 * Integration tests for LIST commands: LPUSH, RPUSH, LPOP, RPOP, LLEN, LRANGE.
 */
public class ListCommandsTest {

    private RedisProtocolHandler handler;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(2));
        handler = new RedisProtocolHandler();
        conn = new MockConnection();
    }

    // Helper: build a RESP command string
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
        byte[] raw = cmd(parts).getBytes();
        handler.handle(conn, ByteBuffer.wrap(raw));
        return conn.getLastResponse();
    }

    // ── LPUSH ─────────────────────────────────────────────────────────────
    @Test

    public void testLpush_newKey_returnsLength1() {
        String resp = exec("LPUSH", "mylist", "a");
        assertEquals(":1\r\n", resp);
    }
    @Test

    public void testLpush_multipleValues_returnsCorrectLength() {
        String resp = exec("LPUSH", "mylist", "a", "b", "c");
        assertEquals(":3\r\n", resp);
    }
    @Test

    public void testLpush_prependsToHead() {
        exec("LPUSH", "mylist", "a");
        exec("LPUSH", "mylist", "b");
        String resp = exec("LRANGE", "mylist", "0", "-1");
        // b was pushed last → [b, a]
        assertTrue(resp.contains("b"), "b should be at head");
        assertTrue(resp.contains("a"), "a should be in list");
        int posB = resp.indexOf("$1\r\nb\r\n");
        int posA = resp.indexOf("$1\r\na\r\n");
        assertTrue(posB < posA, "b should appear before a");
    }

    // ── RPUSH ─────────────────────────────────────────────────────────────
    @Test

    public void testRpush_newKey_returnsLength1() {
        String resp = exec("RPUSH", "mylist", "x");
        assertEquals(":1\r\n", resp);
    }
    @Test

    public void testRpush_appendsToTail() {
        exec("RPUSH", "mylist", "a");
        exec("RPUSH", "mylist", "b");
        String resp = exec("LRANGE", "mylist", "0", "-1");
        // a was pushed first → [a, b]
        int posA = resp.indexOf("$1\r\na\r\n");
        int posB = resp.indexOf("$1\r\nb\r\n");
        assertTrue(posA < posB, "a should appear before b");
    }

    // ── LLEN ──────────────────────────────────────────────────────────────
    @Test

    public void testLlen_emptyKey_returns0() {
        String resp = exec("LLEN", "nosuchkey");
        assertEquals(":0\r\n", resp);
    }
    @Test

    public void testLlen_afterPush_returnsCorrectSize() {
        exec("RPUSH", "mylist", "a", "b", "c");
        String resp = exec("LLEN", "mylist");
        assertEquals(":3\r\n", resp);
    }

    // ── LPOP ──────────────────────────────────────────────────────────────
    @Test

    public void testLpop_emptyKey_returnsNull() {
        String resp = exec("LPOP", "nosuchkey");
        assertEquals("$-1\r\n", resp);
    }
    @Test

    public void testLpop_returnsHead() {
        exec("RPUSH", "mylist", "first", "second");
        String resp = exec("LPOP", "mylist");
        assertEquals("$5\r\nfirst\r\n", resp);
    }
    @Test

    public void testLpop_removesKey_whenEmpty() {
        exec("RPUSH", "mylist", "only");
        exec("LPOP", "mylist");
        // LLEN should return 0 (key deleted)
        String resp = exec("LLEN", "mylist");
        assertEquals(":0\r\n", resp);
    }

    // ── RPOP ──────────────────────────────────────────────────────────────
    @Test

    public void testRpop_emptyKey_returnsNull() {
        String resp = exec("RPOP", "nosuchkey");
        assertEquals("$-1\r\n", resp);
    }
    @Test

    public void testRpop_returnsTail() {
        exec("RPUSH", "mylist", "first", "last");
        String resp = exec("RPOP", "mylist");
        assertEquals("$4\r\nlast\r\n", resp);
    }

    // ── LRANGE ────────────────────────────────────────────────────────────
    @Test

    public void testLrange_fullRange() {
        exec("RPUSH", "mylist", "a", "b", "c");
        String resp = exec("LRANGE", "mylist", "0", "-1");
        assertTrue(resp.contains("$1\r\na\r\n"), "should contain a");
        assertTrue(resp.contains("$1\r\nb\r\n"), "should contain b");
        assertTrue(resp.contains("$1\r\nc\r\n"), "should contain c");
    }
    @Test

    public void testLrange_subRange() {
        exec("RPUSH", "mylist", "a", "b", "c", "d");
        String resp = exec("LRANGE", "mylist", "1", "2");
        assertTrue(resp.contains("$1\r\nb\r\n"), "should contain b");
        assertTrue(resp.contains("$1\r\nc\r\n"), "should contain c");
        assertFalse(resp.contains("$1\r\na\r\n"), "should NOT contain a");
        assertFalse(resp.contains("$1\r\nd\r\n"), "should NOT contain d");
    }
    @Test

    public void testLrange_negativeIndices() {
        exec("RPUSH", "mylist", "a", "b", "c");
        String resp = exec("LRANGE", "mylist", "-2", "-1");
        assertTrue(resp.contains("$1\r\nb\r\n"), "should contain b");
        assertTrue(resp.contains("$1\r\nc\r\n"), "should contain c");
        assertFalse(resp.contains("$1\r\na\r\n"), "should NOT contain a");
    }
    @Test

    public void testLrange_emptyKey_returnsEmptyArray() {
        String resp = exec("LRANGE", "nosuchkey", "0", "-1");
        assertEquals("*0\r\n", resp);
    }

    // ── Type safety ───────────────────────────────────────────────────────
    @Test

    public void testList_wrongType_returnsError() {
        exec("SET", "strkey", "value");
        String resp = exec("LPUSH", "strkey", "newval");
        assertTrue(resp.startsWith("-WRONGTYPE"), "Should return WRONGTYPE error");
    }
}
