package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RespToken;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for INCR / DECR / INCRBY / DECRBY.
 * JUnit5'teki CounterCommandsTest'in custom framework karşılığı.
 */
public class CounterCommandsCustomTest {

    private MockConnection conn;
    private IncrCommand   incr   = new IncrCommand();
    private DecrCommand   decr   = new DecrCommand();
    private IncrByCommand incrBy = new IncrByCommand();
    private DecrByCommand decrBy = new DecrByCommand();
    private SetCommand    set    = new SetCommand();
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(16));
        Db.getInstance().clear();
        conn = new MockConnection();
    }

    // ── INCR ──────────────────────────────────────────────────────────────

    /** INCR yeni key'i 1'den başlatır */
    @Test
    public void testIncr_newKey_startsAt1() {
        incr.execute(conn, args("INCR", "ctr"));
        assertEquals(":1\r\n", conn.getLastResponse());
    }

    /** INCR mevcut key'i bir artırır */
    @Test
    public void testIncr_existingKey_incrementsByOne() {
        incr.execute(conn, args("INCR", "c2"));
        conn.clear();
        incr.execute(conn, args("INCR", "c2"));
        assertEquals(":2\r\n", conn.getLastResponse());
    }

    /** INCR art arda 5 kez → 5 */
    @Test
    public void testIncr_fiveTimes_returns5() {
        for (int i = 0; i < 5; i++) {
            conn.clear();
            incr.execute(conn, args("INCR", "cnt5"));
        }
        assertEquals(":5\r\n", conn.getLastResponse());
    }

    /** INCR hash tipine uygulanırsa WRONGTYPE */
    @Test
    public void testIncr_onWrongType_returnsWRONGTYPE() {
        new HSetCommand().execute(conn, args("HSET", "myhash", "f", "v"));
        conn.clear();
        incr.execute(conn, args("INCR", "myhash"));
        assertTrue(conn.getLastResponse().startsWith("-WRONGTYPE"), "WRONGTYPE hatası");
    }

    /** INCR integer olmayan string'de ERR */
    @Test
    public void testIncr_nonIntegerString_returnsError() {
        set.execute(conn, args("SET", "str", "hello"));
        conn.clear();
        incr.execute(conn, args("INCR", "str"));
        assertTrue(conn.getLastResponse().startsWith("-ERR"), "ERR hatası");
    }

    // ── DECR ──────────────────────────────────────────────────────────────

    /** DECR mevcut değeri bir azaltır */
    @Test
    public void testDecr_existingKey_decrementsBy1() {
        set.execute(conn, args("SET", "d1", "10"));
        conn.clear();
        decr.execute(conn, args("DECR", "d1"));
        assertEquals(":9\r\n", conn.getLastResponse());
    }

    /** DECR yeni key'i -1'den başlatır */
    @Test
    public void testDecr_newKey_startsAtMinus1() {
        decr.execute(conn, args("DECR", "newDecr"));
        assertEquals(":-1\r\n", conn.getLastResponse());
    }

    // ── INCRBY ────────────────────────────────────────────────────────────

    /** INCRBY belirtilen miktarda artırır */
    @Test
    public void testIncrBy_positiveAmount() {
        incrBy.execute(conn, args("INCRBY", "ib1", "5"));
        assertEquals(":5\r\n", conn.getLastResponse());
        conn.clear();
        incrBy.execute(conn, args("INCRBY", "ib1", "10"));
        assertEquals(":15\r\n", conn.getLastResponse());
    }

    /** INCRBY negatif miktar → değeri azaltır */
    @Test
    public void testIncrBy_negativeAmount() {
        set.execute(conn, args("SET", "ib2", "20"));
        conn.clear();
        incrBy.execute(conn, args("INCRBY", "ib2", "-5"));
        assertEquals(":15\r\n", conn.getLastResponse());
    }

    /** INCRBY integer olmayan delta → ERR */
    @Test
    public void testIncrBy_nonIntegerDelta_returnsError() {
        incrBy.execute(conn, args("INCRBY", "ib3", "notanumber"));
        assertTrue(conn.getLastResponse().startsWith("-ERR"), "ERR hatası");
    }

    // ── DECRBY ────────────────────────────────────────────────────────────

    /** DECRBY belirtilen miktarda azaltır */
    @Test
    public void testDecrBy_amount() {
        set.execute(conn, args("SET", "db1", "20"));
        conn.clear();
        decrBy.execute(conn, args("DECRBY", "db1", "5"));
        assertEquals(":15\r\n", conn.getLastResponse());
    }

    /** DECRBY negatif miktar → değeri artırır */
    @Test
    public void testDecrBy_negativeAmount_increases() {
        set.execute(conn, args("SET", "db2", "10"));
        conn.clear();
        decrBy.execute(conn, args("DECRBY", "db2", "-3"));
        assertEquals(":13\r\n", conn.getLastResponse());
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
