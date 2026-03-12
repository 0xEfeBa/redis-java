package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RespToken;
import com.redisjava.testutil.Assert;

/**
 * Unit tests for HSET / HGET / HDEL / HEXISTS / HLEN / HGETALL.
 * JUnit5'teki HashCommandsTest'in custom framework karşılığı.
 */
public class HashCommandsCustomTest {

    private MockConnection conn;
    private HSetCommand    hset    = new HSetCommand();
    private HGetCommand    hget    = new HGetCommand();
    private HDelCommand    hdel    = new HDelCommand();
    private HExistsCommand hexists = new HExistsCommand();
    private HLenCommand    hlen    = new HLenCommand();
    private HGetAllCommand hgetall = new HGetAllCommand();
    private SetCommand     set     = new SetCommand();
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(16));
        Db.getInstance().clear();
        conn = new MockConnection();
    }

    // ── HSET / HGET ───────────────────────────────────────────────────────

    /** HSET yeni field ekler → :1, HGET doğru değeri döner */
    @Test
    public void testHset_newField_returns1_and_hget_works() {
        hset.execute(conn, args("HSET", "user:1", "name", "Ali"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());

        conn.clear();
        hget.execute(conn, args("HGET", "user:1", "name"));
        Assert.assertEquals("$3\r\nAli\r\n", conn.getLastResponse());
    }

    /** HSET mevcut field'ı günceller → :0 */
    @Test
    public void testHset_updateField_returns0() {
        hset.execute(conn, args("HSET", "h1", "f", "v1"));
        conn.clear();
        hset.execute(conn, args("HSET", "h1", "f", "v2"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());

        conn.clear();
        hget.execute(conn, args("HGET", "h1", "f"));
        Assert.assertEquals("$2\r\nv2\r\n", conn.getLastResponse());
    }

    /** HGET mevcut olmayan field → nil */
    @Test
    public void testHget_missingField_returnsNil() {
        hset.execute(conn, args("HSET", "h2", "a", "1"));
        conn.clear();
        hget.execute(conn, args("HGET", "h2", "missing"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
    }

    /** HGET mevcut olmayan key → nil */
    @Test
    public void testHget_missingKey_returnsNil() {
        hget.execute(conn, args("HGET", "nosuchkey", "f"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
    }

    /** HSET birden fazla field aynı anda */
    @Test
    public void testHset_multipleFields() {
        hset.execute(conn, args("HSET", "h3", "f1", "v1", "f2", "v2", "f3", "v3"));
        // 3 yeni field → :3
        Assert.assertEquals(":3\r\n", conn.getLastResponse());

        conn.clear();
        hget.execute(conn, args("HGET", "h3", "f2"));
        Assert.assertEquals("$2\r\nv2\r\n", conn.getLastResponse());
    }

    // ── HDEL ──────────────────────────────────────────────────────────────

    /** HDEL mevcut field siler → :1 */
    @Test
    public void testHdel_existingField_returns1() {
        hset.execute(conn, args("HSET", "h4", "f1", "v1", "f2", "v2"));
        conn.clear();
        hdel.execute(conn, args("HDEL", "h4", "f1"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());

        conn.clear();
        hget.execute(conn, args("HGET", "h4", "f1"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
    }

    /** HDEL mevcut olmayan field → :0 */
    @Test
    public void testHdel_missingField_returns0() {
        hset.execute(conn, args("HSET", "h5", "f1", "v1"));
        conn.clear();
        hdel.execute(conn, args("HDEL", "h5", "ghost"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    /** HDEL birden fazla field */
    @Test
    public void testHdel_multipleFields() {
        hset.execute(conn, args("HSET", "h6", "a", "1", "b", "2", "c", "3"));
        conn.clear();
        hdel.execute(conn, args("HDEL", "h6", "a", "b"));
        Assert.assertEquals(":2\r\n", conn.getLastResponse());
    }

    // ── HEXISTS ───────────────────────────────────────────────────────────

    /** HEXISTS mevcut field → :1 */
    @Test
    public void testHexists_presentField_returns1() {
        hset.execute(conn, args("HSET", "h7", "x", "y"));
        conn.clear();
        hexists.execute(conn, args("HEXISTS", "h7", "x"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
    }

    /** HEXISTS mevcut olmayan field → :0 */
    @Test
    public void testHexists_missingField_returns0() {
        hset.execute(conn, args("HSET", "h8", "x", "y"));
        conn.clear();
        hexists.execute(conn, args("HEXISTS", "h8", "z"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    /** HEXISTS mevcut olmayan key → :0 */
    @Test
    public void testHexists_missingKey_returns0() {
        hexists.execute(conn, args("HEXISTS", "nosuchkey", "f"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    // ── HLEN ──────────────────────────────────────────────────────────────

    /** HLEN field sayısını döner */
    @Test
    public void testHlen_countsFields() {
        hset.execute(conn, args("HSET", "h9", "a", "1", "b", "2"));
        conn.clear();
        hlen.execute(conn, args("HLEN", "h9"));
        Assert.assertEquals(":2\r\n", conn.getLastResponse());
    }

    /** HLEN mevcut olmayan key → :0 */
    @Test
    public void testHlen_missingKey_returns0() {
        hlen.execute(conn, args("HLEN", "nosuchkey"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    // ── HGETALL ───────────────────────────────────────────────────────────

    /** HGETALL tüm field-value çiftlerini döner */
    @Test
    public void testHgetall_returnsPairs() {
        hset.execute(conn, args("HSET", "h10", "a", "1", "b", "2"));
        conn.clear();
        hgetall.execute(conn, args("HGETALL", "h10"));
        String resp = conn.getLastResponse();
        Assert.assertTrue("array header *4", resp.startsWith("*4\r\n"));
        Assert.assertTrue("a içeriyor", resp.contains("a"));
        Assert.assertTrue("1 içeriyor", resp.contains("1"));
        Assert.assertTrue("b içeriyor", resp.contains("b"));
        Assert.assertTrue("2 içeriyor", resp.contains("2"));
    }

    /** HGETALL mevcut olmayan key → boş array */
    @Test
    public void testHgetall_missingKey_returnsEmptyArray() {
        hgetall.execute(conn, args("HGETALL", "nosuchkey"));
        Assert.assertEquals("*0\r\n", conn.getLastResponse());
    }

    // ── WRONGTYPE ─────────────────────────────────────────────────────────

    /** Hash olmayan key'e HSET → WRONGTYPE */
    @Test
    public void testHset_wrongType_returnsError() {
        set.execute(conn, args("SET", "str", "val"));
        conn.clear();
        hset.execute(conn, args("HSET", "str", "f", "v"));
        Assert.assertTrue("WRONGTYPE hatası", conn.getLastResponse().startsWith("-WRONGTYPE"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static RespToken[] args(String... words) {
        RespToken[] tokens = new RespToken[words.length];
        for (int i = 0; i < words.length; i++) {
            tokens[i] = RespToken.bulkString(words[i].getBytes());
        }
        return tokens;
    }
}
