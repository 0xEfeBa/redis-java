package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RespToken;
import com.redisjava.testutil.Assert;

/**
 * Unit tests for EXPIRE / TTL / PERSIST.
 * JUnit5'teki TtlCommandsTest'in custom framework karşılığı.
 */
public class TtlCommandsCustomTest {

    private MockConnection conn;
    private SetCommand     set     = new SetCommand();
    private TtlCommand     ttl     = new TtlCommand();
    private ExpireCommand  expire  = new ExpireCommand();
    private PersistCommand persist = new PersistCommand();
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(16));
        Db.getInstance().clear();
        conn = new MockConnection();
    }

    // ── TTL ───────────────────────────────────────────────────────────────

    /** Mevcut olmayan key → TTL :-2 */
    @Test
    public void testTtl_missingKey_returnsMinus2() {
        ttl.execute(conn, args("TTL", "missing"));
        Assert.assertEquals(":-2\r\n", conn.getLastResponse());
    }

    /** TTL'siz key → :-1 */
    @Test
    public void testTtl_noExpiry_returnsMinus1() {
        set.execute(conn, args("SET", "k", "v"));
        conn.clear();
        ttl.execute(conn, args("TTL", "k"));
        Assert.assertEquals(":-1\r\n", conn.getLastResponse());
    }

    /** TTL süreli key için 0-5 arası döner */
    @Test
    public void testTtl_withExpiry_returnsPositive() {
        set.execute(conn, args("SET", "k1", "v1"));
        conn.clear();
        expire.execute(conn, args("EXPIRE", "k1", "5"));
        conn.clear();
        ttl.execute(conn, args("TTL", "k1"));
        String resp = conn.getLastResponse();
        Assert.assertTrue("TTL ile başlar", resp.startsWith(":"));
        long val = Long.parseLong(resp.substring(1, resp.length() - 2));
        Assert.assertTrue("0 ile 5 arasında", val >= 0 && val <= 5);
    }

    // ── EXPIRE ────────────────────────────────────────────────────────────

    /** EXPIRE mevcut key'e TTL koyar → :1 */
    @Test
    public void testExpire_existingKey_returns1() {
        set.execute(conn, args("SET", "e1", "v"));
        conn.clear();
        expire.execute(conn, args("EXPIRE", "e1", "10"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
    }

    /** EXPIRE mevcut olmayan key → :0 */
    @Test
    public void testExpire_missingKey_returns0() {
        expire.execute(conn, args("EXPIRE", "ghost", "5"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    /** EXPIRE 0 süresi key'i siler */
    @Test
    public void testExpire_zero_deletesKey() {
        set.execute(conn, args("SET", "e2", "v"));
        conn.clear();
        expire.execute(conn, args("EXPIRE", "e2", "0"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
        Assert.assertNull(Db.getInstance().getObject("e2".getBytes()));
    }

    /** EXPIRE negatif süre key'i siler */
    @Test
    public void testExpire_negative_deletesKey() {
        set.execute(conn, args("SET", "e3", "v"));
        conn.clear();
        expire.execute(conn, args("EXPIRE", "e3", "-1"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
        Assert.assertNull(Db.getInstance().getObject("e3".getBytes()));
    }

    /** SET EX ile TTL direkt set edilir */
    @Test
    public void testSet_withEx_setsTtl() {
        set.execute(conn, args("SET", "e4", "v", "EX", "5"));
        conn.clear();
        ttl.execute(conn, args("TTL", "e4"));
        String resp = conn.getLastResponse();
        long val = Long.parseLong(resp.substring(1, resp.length() - 2));
        Assert.assertTrue("0 ile 5 arasında", val >= 0 && val <= 5);
    }

    // ── PERSIST ───────────────────────────────────────────────────────────

    /** PERSIST TTL'yi kaldırır → :1, sonra TTL :-1 */
    @Test
    public void testPersist_removesTtl() {
        set.execute(conn, args("SET", "p1", "v", "EX", "5"));
        conn.clear();
        persist.execute(conn, args("PERSIST", "p1"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());

        conn.clear();
        ttl.execute(conn, args("TTL", "p1"));
        Assert.assertEquals(":-1\r\n", conn.getLastResponse());
    }

    /** PERSIST TTL'siz key'de → :0 */
    @Test
    public void testPersist_noExpiry_returns0() {
        set.execute(conn, args("SET", "p2", "v"));
        conn.clear();
        persist.execute(conn, args("PERSIST", "p2"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    /** PERSIST mevcut olmayan key → :0 */
    @Test
    public void testPersist_missingKey_returns0() {
        persist.execute(conn, args("PERSIST", "ghost"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
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
