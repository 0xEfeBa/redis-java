package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RedisProtocolHandler;
import com.redisjava.testutil.Assert;

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
        Assert.assertEquals(":1\r\n", resp);
    }
    @Test

    public void testLpush_multipleValues_returnsCorrectLength() {
        String resp = exec("LPUSH", "mylist", "a", "b", "c");
        Assert.assertEquals(":3\r\n", resp);
    }
    @Test

    public void testLpush_prependsToHead() {
        exec("LPUSH", "mylist", "a");
        exec("LPUSH", "mylist", "b");
        String resp = exec("LRANGE", "mylist", "0", "-1");
        // b was pushed last → [b, a]
        Assert.assertTrue("b should be at head", resp.contains("b"));
        Assert.assertTrue("a should be in list", resp.contains("a"));
        int posB = resp.indexOf("$1\r\nb\r\n");
        int posA = resp.indexOf("$1\r\na\r\n");
        Assert.assertTrue("b should appear before a", posB < posA);
    }

    // ── RPUSH ─────────────────────────────────────────────────────────────
    @Test

    public void testRpush_newKey_returnsLength1() {
        String resp = exec("RPUSH", "mylist", "x");
        Assert.assertEquals(":1\r\n", resp);
    }
    @Test

    public void testRpush_appendsToTail() {
        exec("RPUSH", "mylist", "a");
        exec("RPUSH", "mylist", "b");
        String resp = exec("LRANGE", "mylist", "0", "-1");
        // a was pushed first → [a, b]
        int posA = resp.indexOf("$1\r\na\r\n");
        int posB = resp.indexOf("$1\r\nb\r\n");
        Assert.assertTrue("a should appear before b", posA < posB);
    }

    // ── LLEN ──────────────────────────────────────────────────────────────
    @Test

    public void testLlen_emptyKey_returns0() {
        String resp = exec("LLEN", "nosuchkey");
        Assert.assertEquals(":0\r\n", resp);
    }
    @Test

    public void testLlen_afterPush_returnsCorrectSize() {
        exec("RPUSH", "mylist", "a", "b", "c");
        String resp = exec("LLEN", "mylist");
        Assert.assertEquals(":3\r\n", resp);
    }

    // ── LPOP ──────────────────────────────────────────────────────────────
    @Test

    public void testLpop_emptyKey_returnsNull() {
        String resp = exec("LPOP", "nosuchkey");
        Assert.assertEquals("$-1\r\n", resp);
    }
    @Test

    public void testLpop_returnsHead() {
        exec("RPUSH", "mylist", "first", "second");
        String resp = exec("LPOP", "mylist");
        Assert.assertEquals("$5\r\nfirst\r\n", resp);
    }
    @Test

    public void testLpop_removesKey_whenEmpty() {
        exec("RPUSH", "mylist", "only");
        exec("LPOP", "mylist");
        // LLEN should return 0 (key deleted)
        String resp = exec("LLEN", "mylist");
        Assert.assertEquals(":0\r\n", resp);
    }

    // ── RPOP ──────────────────────────────────────────────────────────────
    @Test

    public void testRpop_emptyKey_returnsNull() {
        String resp = exec("RPOP", "nosuchkey");
        Assert.assertEquals("$-1\r\n", resp);
    }
    @Test

    public void testRpop_returnsTail() {
        exec("RPUSH", "mylist", "first", "last");
        String resp = exec("RPOP", "mylist");
        Assert.assertEquals("$4\r\nlast\r\n", resp);
    }

    // ── LRANGE ────────────────────────────────────────────────────────────
    @Test

    public void testLrange_fullRange() {
        exec("RPUSH", "mylist", "a", "b", "c");
        String resp = exec("LRANGE", "mylist", "0", "-1");
        Assert.assertTrue("should contain a", resp.contains("$1\r\na\r\n"));
        Assert.assertTrue("should contain b", resp.contains("$1\r\nb\r\n"));
        Assert.assertTrue("should contain c", resp.contains("$1\r\nc\r\n"));
    }
    @Test

    public void testLrange_subRange() {
        exec("RPUSH", "mylist", "a", "b", "c", "d");
        String resp = exec("LRANGE", "mylist", "1", "2");
        Assert.assertTrue("should contain b", resp.contains("$1\r\nb\r\n"));
        Assert.assertTrue("should contain c", resp.contains("$1\r\nc\r\n"));
        Assert.assertFalse("should NOT contain a", resp.contains("$1\r\na\r\n"));
        Assert.assertFalse("should NOT contain d", resp.contains("$1\r\nd\r\n"));
    }
    @Test

    public void testLrange_negativeIndices() {
        exec("RPUSH", "mylist", "a", "b", "c");
        String resp = exec("LRANGE", "mylist", "-2", "-1");
        Assert.assertTrue("should contain b", resp.contains("$1\r\nb\r\n"));
        Assert.assertTrue("should contain c", resp.contains("$1\r\nc\r\n"));
        Assert.assertFalse("should NOT contain a", resp.contains("$1\r\na\r\n"));
    }
    @Test

    public void testLrange_emptyKey_returnsEmptyArray() {
        String resp = exec("LRANGE", "nosuchkey", "0", "-1");
        Assert.assertEquals("*0\r\n", resp);
    }

    // ── Type safety ───────────────────────────────────────────────────────
    @Test

    public void testList_wrongType_returnsError() {
        exec("SET", "strkey", "value");
        String resp = exec("LPUSH", "strkey", "newval");
        Assert.assertTrue("Should return WRONGTYPE error", resp.startsWith("-WRONGTYPE"));
    }
}
