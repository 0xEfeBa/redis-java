package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RespToken;
import com.redisjava.testutil.Assert;

/**
 * Unit tests for DEL, EXISTS, FLUSHALL, ECHO, INFO ve locking komutları
 * (EXISTS+SETNX+SET NX/XX).
 * JUnit5'teki LockingCommandsTest + InfoCommandFormatTest karşılığı.
 */
public class MiscCommandsTest {

    private MockConnection conn;
    private SetCommand      set      = new SetCommand();
    private GetCommand      get      = new GetCommand();
    private DelCommand      del      = new DelCommand();
    private ExistsCommand   exists   = new ExistsCommand();
    private FlushAllCommand flushAll = new FlushAllCommand();
    private EchoCommand     echo     = new EchoCommand();
    private InfoCommand     info     = new InfoCommand();
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(16));
        Db.getInstance().clear();
        conn = new MockConnection();
    }

    // ── DEL ───────────────────────────────────────────────────────────────

    /** DEL tek key siler → :1 */
    @Test
    public void testDel_singleKey_returns1() {
        set.execute(conn, args("SET", "k", "v"));
        conn.clear();
        del.execute(conn, args("DEL", "k"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
    }

    /** DEL sonrası GET nil döner */
    @Test
    public void testDel_afterDel_getReturnsNil() {
        set.execute(conn, args("SET", "k", "v"));
        del.execute(conn, args("DEL", "k"));
        conn.clear();
        get.execute(conn, args("GET", "k"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
    }

    /** DEL birden fazla key siler */
    @Test
    public void testDel_multipleKeys() {
        set.execute(conn, args("SET", "a", "1"));
        set.execute(conn, args("SET", "b", "2"));
        set.execute(conn, args("SET", "c", "3"));
        conn.clear();
        del.execute(conn, args("DEL", "a", "b", "c"));
        Assert.assertEquals(":3\r\n", conn.getLastResponse());
    }

    /** DEL mevcut olmayan key → :0 */
    @Test
    public void testDel_missingKey_returns0() {
        del.execute(conn, args("DEL", "ghost"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    /** DEL karışık key listesi (bazı mevcut, bazı yok) */
    @Test
    public void testDel_mixed_returnsExistingCount() {
        set.execute(conn, args("SET", "x", "1"));
        conn.clear();
        del.execute(conn, args("DEL", "x", "missing1", "missing2"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
    }

    // ── EXISTS ────────────────────────────────────────────────────────────

    /** EXISTS mevcut olmayan key → :0 */
    @Test
    public void testExists_missingKey_returns0() {
        exists.execute(conn, args("EXISTS", "ghost"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    /** EXISTS mevcut key → :1 */
    @Test
    public void testExists_presentKey_returns1() {
        set.execute(conn, args("SET", "e1", "v"));
        conn.clear();
        exists.execute(conn, args("EXISTS", "e1"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
    }

    /** EXISTS birden fazla key — mevcut olanları sayar */
    @Test
    public void testExists_multipleKeys_countPresent() {
        set.execute(conn, args("SET", "e2", "v"));
        set.execute(conn, args("SET", "e3", "v"));
        conn.clear();
        exists.execute(conn, args("EXISTS", "e2", "e3", "ghost"));
        Assert.assertEquals(":2\r\n", conn.getLastResponse());
    }

    /** EXISTS DEL sonrası → :0 */
    @Test
    public void testExists_afterDel_returns0() {
        set.execute(conn, args("SET", "e4", "v"));
        del.execute(conn, args("DEL", "e4"));
        conn.clear();
        exists.execute(conn, args("EXISTS", "e4"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    // ── FLUSHALL ──────────────────────────────────────────────────────────

    /** FLUSHALL tüm key'leri siler */
    @Test
    public void testFlushAll_clearsAllKeys() {
        set.execute(conn, args("SET", "a", "1"));
        set.execute(conn, args("SET", "b", "2"));
        set.execute(conn, args("SET", "c", "3"));
        conn.clear();
        flushAll.execute(conn, args("FLUSHALL"));
        Assert.assertEquals("+OK\r\n", conn.getLastResponse());

        conn.clear();
        get.execute(conn, args("GET", "a"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
    }

    /** FLUSHALL sonrası EXISTS → :0 */
    @Test
    public void testFlushAll_afterFlush_existsReturns0() {
        set.execute(conn, args("SET", "k", "v"));
        flushAll.execute(conn, args("FLUSHALL"));
        conn.clear();
        exists.execute(conn, args("EXISTS", "k"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
    }

    // ── ECHO ──────────────────────────────────────────────────────────────

    /** ECHO argümanı geri döner */
    @Test
    public void testEcho_returnsInput() {
        echo.execute(conn, args("ECHO", "hello"));
        Assert.assertEquals("$5\r\nhello\r\n", conn.getLastResponse());
    }

    /** ECHO boş string */
    @Test
    public void testEcho_emptyString() {
        echo.execute(conn, args("ECHO", ""));
        Assert.assertEquals("$0\r\n\r\n", conn.getLastResponse());
    }

    // ── INFO ──────────────────────────────────────────────────────────────

    /** INFO yanıtı boş olmayan string döner */
    @Test
    public void testInfo_returnsNonEmpty() {
        Db.init(new MemoryManager(16)); // INFO MemoryManager gerektirir
        info.execute(conn, args("INFO"));
        String resp = conn.getLastResponse();
        Assert.assertNotNull(resp);
        Assert.assertTrue("INFO yanıtı boş değil", resp.length() > 0);
        Assert.assertTrue("RESP bulk formatında", resp.startsWith("$"));
    }

    /** INFO formatHumanReadable özel değerleri (reflection ile) */
    public void testInfo_formatHumanReadable_units() throws Exception {
        java.lang.reflect.Method m =
            InfoCommand.class.getDeclaredMethod("formatHumanReadable", long.class);
        m.setAccessible(true);
        InfoCommand cmd = new InfoCommand();

        Assert.assertEquals("0B",      m.invoke(cmd, 0L));
        Assert.assertEquals("100B",    m.invoke(cmd, 100L));
        Assert.assertEquals("1.00KB",  m.invoke(cmd, 1024L));
        Assert.assertEquals("1.50KB",  m.invoke(cmd, 1536L));
        Assert.assertEquals("1.00MB",  m.invoke(cmd, 1048576L));
        Assert.assertEquals("1.00GB",  m.invoke(cmd, 1073741824L));
    }

    // ── Locking (SETNX + SET NX/XX) ──────────────────────────────────────

    /** SETNX yeni key → :1 */
    @Test
    public void testSetnx_newKey_returns1() {
        set.execute(conn, args("SETNX", "lock", "1"));
        Assert.assertEquals(":1\r\n", conn.getLastResponse());
    }

    /** SETNX mevcut key → :0, değer değişmez */
    @Test
    public void testSetnx_existingKey_returns0_doesNotOverwrite() {
        set.execute(conn, args("SETNX", "lock2", "original"));
        conn.clear();
        set.execute(conn, args("SETNX", "lock2", "new"));
        Assert.assertEquals(":0\r\n", conn.getLastResponse());
        conn.clear();
        get.execute(conn, args("GET", "lock2"));
        Assert.assertEquals("$8\r\noriginal\r\n", conn.getLastResponse());
    }

    /** SET key val NX — key yoksa set eder */
    @Test
    public void testSet_nx_newKey_returnsOk() {
        set.execute(conn, args("SET", "nx1", "v", "NX"));
        Assert.assertEquals("+OK\r\n", conn.getLastResponse());
    }

    /** SET key val NX — key varsa nil döner */
    @Test
    public void testSet_nx_existingKey_returnsNil() {
        set.execute(conn, args("SET", "nx2", "first"));
        conn.clear();
        set.execute(conn, args("SET", "nx2", "second", "NX"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
    }

    /** SET key val XX — key varsa set eder */
    @Test
    public void testSet_xx_existingKey_returnsOk() {
        set.execute(conn, args("SET", "xx1", "v1"));
        conn.clear();
        set.execute(conn, args("SET", "xx1", "v2", "XX"));
        Assert.assertEquals("+OK\r\n", conn.getLastResponse());
    }

    /** SET key val XX — key yoksa nil döner */
    @Test
    public void testSet_xx_missingKey_returnsNil() {
        set.execute(conn, args("SET", "xx2", "v", "XX"));
        Assert.assertEquals("$-1\r\n", conn.getLastResponse());
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
