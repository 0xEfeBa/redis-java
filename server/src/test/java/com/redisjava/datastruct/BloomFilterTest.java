package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.redisjava.command.BfAddCommand;
import com.redisjava.command.BfExistsCommand;
import com.redisjava.command.BfReserveCommand;
import com.redisjava.command.MockConnection;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RespToken;

/**
 * Tests for BloomFilter data structure and BF.* commands.
 */
public class BloomFilterTest {

    private BfAddCommand bfAdd;
    private BfExistsCommand bfExists;
    private BfReserveCommand bfReserve;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        MemoryManager mem = new MemoryManager(16);
        Db.init(mem);
        bfAdd     = new BfAddCommand();
        bfExists  = new BfExistsCommand();
        bfReserve = new BfReserveCommand();
        conn = new MockConnection();
    }

    // ── BloomFilter core ───────────────────────────────────────────────────

    /** Newly added item is always found (no false negatives) */
    @Test
    public void testBloom_noFalseNegatives() {
        BloomFilter bf = new BloomFilter(1000, 0.01);
        String[] items = {"apple", "banana", "cherry", "date", "elderberry"};
        for (String item : items) {
            bf.add(item.getBytes());
        }
        for (String item : items) {
            assertTrue(bf.mightContain(item.getBytes()), "should contain " + item);
        }
    }

    /** Definitely-absent item returns false most of the time */
    @Test
    public void testBloom_absentItem_returnsFalse() {
        BloomFilter bf = new BloomFilter(1000, 0.01);
        bf.add("present".getBytes());
        // "absent" was never added — should return false
        // (could be true by false positive, but with 1000 capacity / 1% rate,
        //  the probability of a FP for a single fresh filter is ~1%, acceptable)
        boolean result = bf.mightContain("definitely-not-present-12345".getBytes());
        // We assert false here because this specific value shouldn't be a FP
        // for a freshly initialized filter with only one element.
        assertFalse(result, "absent item should return false");
    }

    /** count() increments on each add() */
    @Test
    public void testBloom_countTracking() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        assertEquals(0L, bf.count());
        bf.add("a".getBytes());
        assertEquals(1L, bf.count());
        bf.add("b".getBytes());
        assertEquals(2L, bf.count());
    }

    /** False positive rate stays within configured bounds over many items */
    @Test
    public void testBloom_falsePositiveRate() {
        int capacity = 1000;
        double errorRate = 0.05;
        BloomFilter bf = new BloomFilter(capacity, errorRate);

        // Add 1000 items
        for (int i = 0; i < capacity; i++) {
            bf.add(("item-" + i).getBytes());
        }

        // Test 500 items that were never added — count false positives
        int falsePositives = 0;
        for (int i = capacity; i < capacity + 500; i++) {
            if (bf.mightContain(("item-" + i).getBytes())) {
                falsePositives++;
            }
        }
        double fpRate = (double) falsePositives / 500;
        // Allow 3x the configured rate as tolerance
        assertTrue(fpRate <= errorRate * 3,
                "FP rate " + fpRate + " exceeds 3x configured " + errorRate);
    }

    /** Bit count and hash count are non-zero */
    @Test
    public void testBloom_parameters() {
        BloomFilter bf = new BloomFilter(500, 0.02);
        assertTrue(bf.getBitCount() > 0, "bit count > 0");
        assertTrue(bf.getHashCount() > 0, "hash count > 0");
    }

    /** Invalid parameters throw */
    @Test
    public void testBloom_invalidParams_throws() {
        boolean threw = false;
        try {
            new BloomFilter(0, 0.01);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw, "zero capacity should throw");

        threw = false;
        try {
            new BloomFilter(100, 0.0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw, "zero error rate should throw");
    }

    // ── BF.ADD ────────────────────────────────────────────────────────────

    /** BF.ADD creates filter and adds item, returns :1 for new item */
    @Test
    public void testBfAdd_newItem_returns1() {
        bfAdd.execute(conn, tokens("BF.ADD", "myfilter", "foo"));
        assertTrue(conn.getLastResponse().contains(":1\r\n"), "returns :1");
    }

    /** BF.ADD returns :0 for already-present item */
    @Test
    public void testBfAdd_existingItem_returns0() {
        bfAdd.execute(conn, tokens("BF.ADD", "myfilter2", "bar"));
        conn.clear();
        bfAdd.execute(conn, tokens("BF.ADD", "myfilter2", "bar"));
        assertTrue(conn.getLastResponse().contains(":0\r\n"), "returns :0");
    }

    /** BF.ADD wrong arg count returns error */
    @Test
    public void testBfAdd_wrongArgs_returnsError() {
        bfAdd.execute(conn, tokens("BF.ADD", "onlykey"));
        assertTrue(conn.getLastResponse().startsWith("-"), "error");
    }

    // ── BF.EXISTS ────────────────────────────────────────────────────────

    /** BF.EXISTS returns :1 for added item */
    @Test
    public void testBfExists_addedItem_returns1() {
        bfAdd.execute(conn, tokens("BF.ADD", "ef1", "hello"));
        conn.clear();
        bfExists.execute(conn, tokens("BF.EXISTS", "ef1", "hello"));
        assertTrue(conn.getLastResponse().contains(":1\r\n"), "exists returns :1");
    }

    /** BF.EXISTS returns :0 for missing key */
    @Test
    public void testBfExists_missingKey_returns0() {
        bfExists.execute(conn, tokens("BF.EXISTS", "no-such-key", "x"));
        assertTrue(conn.getLastResponse().contains(":0\r\n"), "missing key returns :0");
    }

    /** BF.EXISTS returns :0 for absent item */
    @Test
    public void testBfExists_absentItem_returns0() {
        bfAdd.execute(conn, tokens("BF.ADD", "ef2", "present"));
        conn.clear();
        bfExists.execute(conn, tokens("BF.EXISTS", "ef2", "definitely-not-present-99999"));
        assertTrue(conn.getLastResponse().contains(":0\r\n"), "absent returns :0");
    }

    // ── BF.RESERVE ───────────────────────────────────────────────────────

    /** BF.RESERVE creates filter with custom params, returns +OK */
    @Test
    public void testBfReserve_createsFilter() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "bigfilter", "0.001", "100000"));
        assertTrue(conn.getLastResponse().contains("+OK"), "returns OK");

        // Verify by adding an item
        conn.clear();
        bfAdd.execute(conn, tokens("BF.ADD", "bigfilter", "testmember"));
        assertTrue(conn.getLastResponse().contains(":1\r\n"), "add succeeds");
    }

    /** BF.RESERVE on existing key returns error */
    @Test
    public void testBfReserve_existingKey_returnsError() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "dup", "0.01", "100"));
        conn.clear();
        bfReserve.execute(conn, tokens("BF.RESERVE", "dup", "0.01", "100"));
        assertTrue(conn.getLastResponse().startsWith("-"), "error on existing key");
    }

    /** BF.RESERVE with invalid error rate returns error */
    @Test
    public void testBfReserve_invalidErrorRate_returnsError() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "bad", "1.5", "100"));
        assertTrue(conn.getLastResponse().startsWith("-"), "invalid rate error");
    }

    /** BF.RESERVE with invalid capacity returns error */
    @Test
    public void testBfReserve_invalidCapacity_returnsError() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "bad2", "0.01", "notanumber"));
        assertTrue(conn.getLastResponse().startsWith("-"), "invalid capacity error");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private RespToken[] tokens(String... parts) {
        RespToken[] bulks = new RespToken[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bulks[i] = RespToken.bulkString(parts[i].getBytes());
        }
        return bulks;
    }
}
