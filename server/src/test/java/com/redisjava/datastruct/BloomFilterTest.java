package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;
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
            Assert.assertTrue("should contain " + item, bf.mightContain(item.getBytes()));
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
        Assert.assertFalse("absent item should return false", result);
    }

    /** count() increments on each add() */
    @Test
    public void testBloom_countTracking() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        Assert.assertEquals(0L, bf.count());
        bf.add("a".getBytes());
        Assert.assertEquals(1L, bf.count());
        bf.add("b".getBytes());
        Assert.assertEquals(2L, bf.count());
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
        Assert.assertTrue("FP rate " + fpRate + " exceeds 3x configured " + errorRate,
                fpRate <= errorRate * 3);
    }

    /** Bit count and hash count are non-zero */
    @Test
    public void testBloom_parameters() {
        BloomFilter bf = new BloomFilter(500, 0.02);
        Assert.assertTrue("bit count > 0", bf.getBitCount() > 0);
        Assert.assertTrue("hash count > 0", bf.getHashCount() > 0);
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
        Assert.assertTrue("zero capacity should throw", threw);

        threw = false;
        try {
            new BloomFilter(100, 0.0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        Assert.assertTrue("zero error rate should throw", threw);
    }

    // ── BF.ADD ────────────────────────────────────────────────────────────

    /** BF.ADD creates filter and adds item, returns :1 for new item */
    @Test
    public void testBfAdd_newItem_returns1() {
        bfAdd.execute(conn, tokens("BF.ADD", "myfilter", "foo"));
        Assert.assertTrue("returns :1", conn.getLastResponse().contains(":1\r\n"));
    }

    /** BF.ADD returns :0 for already-present item */
    @Test
    public void testBfAdd_existingItem_returns0() {
        bfAdd.execute(conn, tokens("BF.ADD", "myfilter2", "bar"));
        conn.clear();
        bfAdd.execute(conn, tokens("BF.ADD", "myfilter2", "bar"));
        Assert.assertTrue("returns :0", conn.getLastResponse().contains(":0\r\n"));
    }

    /** BF.ADD wrong arg count returns error */
    @Test
    public void testBfAdd_wrongArgs_returnsError() {
        bfAdd.execute(conn, tokens("BF.ADD", "onlykey"));
        Assert.assertTrue("error", conn.getLastResponse().startsWith("-"));
    }

    // ── BF.EXISTS ────────────────────────────────────────────────────────

    /** BF.EXISTS returns :1 for added item */
    @Test
    public void testBfExists_addedItem_returns1() {
        bfAdd.execute(conn, tokens("BF.ADD", "ef1", "hello"));
        conn.clear();
        bfExists.execute(conn, tokens("BF.EXISTS", "ef1", "hello"));
        Assert.assertTrue("exists returns :1", conn.getLastResponse().contains(":1\r\n"));
    }

    /** BF.EXISTS returns :0 for missing key */
    @Test
    public void testBfExists_missingKey_returns0() {
        bfExists.execute(conn, tokens("BF.EXISTS", "no-such-key", "x"));
        Assert.assertTrue("missing key returns :0", conn.getLastResponse().contains(":0\r\n"));
    }

    /** BF.EXISTS returns :0 for absent item */
    @Test
    public void testBfExists_absentItem_returns0() {
        bfAdd.execute(conn, tokens("BF.ADD", "ef2", "present"));
        conn.clear();
        bfExists.execute(conn, tokens("BF.EXISTS", "ef2", "definitely-not-present-99999"));
        Assert.assertTrue("absent returns :0", conn.getLastResponse().contains(":0\r\n"));
    }

    // ── BF.RESERVE ───────────────────────────────────────────────────────

    /** BF.RESERVE creates filter with custom params, returns +OK */
    @Test
    public void testBfReserve_createsFilter() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "bigfilter", "0.001", "100000"));
        Assert.assertTrue("returns OK", conn.getLastResponse().contains("+OK"));

        // Verify by adding an item
        conn.clear();
        bfAdd.execute(conn, tokens("BF.ADD", "bigfilter", "testmember"));
        Assert.assertTrue("add succeeds", conn.getLastResponse().contains(":1\r\n"));
    }

    /** BF.RESERVE on existing key returns error */
    @Test
    public void testBfReserve_existingKey_returnsError() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "dup", "0.01", "100"));
        conn.clear();
        bfReserve.execute(conn, tokens("BF.RESERVE", "dup", "0.01", "100"));
        Assert.assertTrue("error on existing key", conn.getLastResponse().startsWith("-"));
    }

    /** BF.RESERVE with invalid error rate returns error */
    @Test
    public void testBfReserve_invalidErrorRate_returnsError() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "bad", "1.5", "100"));
        Assert.assertTrue("invalid rate error", conn.getLastResponse().startsWith("-"));
    }

    /** BF.RESERVE with invalid capacity returns error */
    @Test
    public void testBfReserve_invalidCapacity_returnsError() {
        bfReserve.execute(conn, tokens("BF.RESERVE", "bad2", "0.01", "notanumber"));
        Assert.assertTrue("invalid capacity error", conn.getLastResponse().startsWith("-"));
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
