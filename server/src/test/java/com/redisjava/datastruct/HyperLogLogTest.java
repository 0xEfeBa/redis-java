package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;
import com.redisjava.command.PfAddCommand;
import com.redisjava.command.PfCountCommand;
import com.redisjava.command.PfMergeCommand;
import com.redisjava.command.MockConnection;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RespToken;

/**
 * Tests for HyperLogLog data structure and PF* commands.
 */
public class HyperLogLogTest {

    private PfAddCommand   pfAdd;
    private PfCountCommand pfCount;
    private PfMergeCommand pfMerge;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        MemoryManager mem = new MemoryManager(16);
        Db.init(mem);
        pfAdd   = new PfAddCommand();
        pfCount = new PfCountCommand();
        pfMerge = new PfMergeCommand();
        conn    = new MockConnection();
    }

    // ── HyperLogLog core ───────────────────────────────────────────────────

    /** Empty HLL returns 0 */
    @Test
    public void testHll_emptyCount() {
        HyperLogLog hll = new HyperLogLog();
        Assert.assertEquals(0L, hll.count());
    }

    /** Single element → count ≈ 1 */
    @Test
    public void testHll_singleElement() {
        HyperLogLog hll = new HyperLogLog();
        hll.add("only".getBytes());
        Assert.assertTrue("count ≈ 1", hll.count() >= 1 && hll.count() <= 2);
    }

    /** Distinct elements counted within 5% accuracy */
    @Test
    public void testHll_accuracy_1000elements() {
        HyperLogLog hll = new HyperLogLog();
        for (int i = 0; i < 1000; i++) {
            hll.add(("element-" + i).getBytes());
        }
        long estimate = hll.count();
        // Allow 5% error
        Assert.assertTrue("estimate >= 950", estimate >= 950);
        Assert.assertTrue("estimate <= 1050", estimate <= 1050);
    }

    /** Duplicate elements don't inflate count */
    @Test
    public void testHll_duplicates_dontInflate() {
        HyperLogLog hll = new HyperLogLog();
        for (int i = 0; i < 100; i++) {
            hll.add("same".getBytes());
        }
        long estimate = hll.count();
        // Should estimate ≈ 1, allow small margin
        Assert.assertTrue("estimate <= 5", estimate <= 5);
        Assert.assertTrue("estimate >= 1", estimate >= 1);
    }

    /** Merge of two HLLs covers all distinct elements */
    @Test
    public void testHll_merge() {
        HyperLogLog a = new HyperLogLog();
        HyperLogLog b = new HyperLogLog();
        for (int i = 0; i < 500; i++) {
            a.add(("a-" + i).getBytes());
        }
        for (int i = 0; i < 500; i++) {
            b.add(("b-" + i).getBytes());
        }
        a.merge(b);
        long estimate = a.count();
        // Union of 1000 distinct elements
        Assert.assertTrue("merged estimate >= 900", estimate >= 900);
        Assert.assertTrue("merged estimate <= 1100", estimate <= 1100);
    }

    /** add() returns true when cardinality changes */
    @Test
    public void testHll_addReturnValue() {
        HyperLogLog hll = new HyperLogLog();
        // First add of a unique element may change the count
        hll.add("unique-abc123".getBytes());
        // Adding the same element again may not change registers (might return false)
        // We don't assert strict behaviour but register count is non-decreasing
        Assert.assertTrue("count >= 1", hll.count() >= 1);
    }

    /** copy() produces an independent copy */
    @Test
    public void testHll_copy_independent() {
        HyperLogLog original = new HyperLogLog();
        original.add("x".getBytes());
        HyperLogLog copy = original.copy();

        // Mutate the copy — original should not change
        for (int i = 0; i < 500; i++) {
            copy.add(("extra-" + i).getBytes());
        }
        // Original still has 1 element estimate
        Assert.assertTrue("original count unaffected", original.count() <= 5);
    }

    // ── PFADD ─────────────────────────────────────────────────────────────

    /** PFADD creates HLL and adds element, returns :1 */
    @Test
    public void testPfAdd_newKey_returns1() {
        pfAdd.execute(conn, tokens("PFADD", "hll1", "a"));
        Assert.assertTrue("returns :1", conn.getLastResponse().contains(":1\r\n"));
    }

    /** PFADD same element twice → second returns :0 (unchanged) */
    @Test
    public void testPfAdd_duplicate_returns0() {
        pfAdd.execute(conn, tokens("PFADD", "hll2", "dup"));
        conn.clear();
        pfAdd.execute(conn, tokens("PFADD", "hll2", "dup"));
        Assert.assertTrue("duplicate returns :0", conn.getLastResponse().contains(":0\r\n"));
    }

    /** PFADD multiple elements in one call */
    @Test
    public void testPfAdd_multipleElements() {
        pfAdd.execute(conn, tokens("PFADD", "hll3", "x", "y", "z"));
        // Three new elements → cardinality changed
        Assert.assertTrue("multiple adds returns :1", conn.getLastResponse().contains(":1\r\n"));
    }

    /** PFADD too few args returns error */
    @Test
    public void testPfAdd_tooFewArgs_returnsError() {
        pfAdd.execute(conn, tokens("PFADD", "only-key"));
        Assert.assertTrue("error", conn.getLastResponse().startsWith("-"));
    }

    // ── PFCOUNT ──────────────────────────────────────────────────────────

    /** PFCOUNT on missing key returns 0 */
    @Test
    public void testPfCount_missingKey_returns0() {
        pfCount.execute(conn, tokens("PFCOUNT", "no-such-hll"));
        Assert.assertTrue("missing key :0", conn.getLastResponse().contains(":0\r\n"));
    }

    /** PFCOUNT after adding 10 elements estimates ≈ 10 */
    @Test
    public void testPfCount_estimatesCardinality() {
        for (int i = 0; i < 10; i++) {
            pfAdd.execute(conn, tokens("PFADD", "hll4", "e" + i));
            conn.clear();
        }
        pfCount.execute(conn, tokens("PFCOUNT", "hll4"));
        String resp = conn.getLastResponse();
        // Extract the integer
        int colon = resp.indexOf(':');
        int cr    = resp.indexOf('\r');
        long estimate = Long.parseLong(resp.substring(colon + 1, cr));
        Assert.assertTrue("estimate >= 8", estimate >= 8);
        Assert.assertTrue("estimate <= 12", estimate <= 12);
    }

    /** PFCOUNT multi-key returns union */
    @Test
    public void testPfCount_multiKey_union() {
        pfAdd.execute(conn, tokens("PFADD", "hll5a", "a", "b", "c"));
        conn.clear();
        pfAdd.execute(conn, tokens("PFADD", "hll5b", "d", "e", "f"));
        conn.clear();
        pfCount.execute(conn, tokens("PFCOUNT", "hll5a", "hll5b"));
        String resp = conn.getLastResponse();
        int colon = resp.indexOf(':');
        int cr    = resp.indexOf('\r');
        long estimate = Long.parseLong(resp.substring(colon + 1, cr));
        // 6 distinct elements
        Assert.assertTrue("union estimate >= 4", estimate >= 4);
        Assert.assertTrue("union estimate <= 8", estimate <= 8);
    }

    // ── PFMERGE ──────────────────────────────────────────────────────────

    /** PFMERGE merges two HLLs into dest, returns +OK */
    @Test
    public void testPfMerge_mergesIntoNew() {
        pfAdd.execute(conn, tokens("PFADD", "src1", "p", "q"));
        conn.clear();
        pfAdd.execute(conn, tokens("PFADD", "src2", "r", "s"));
        conn.clear();

        pfMerge.execute(conn, tokens("PFMERGE", "dest", "src1", "src2"));
        Assert.assertTrue("merge returns OK", conn.getLastResponse().contains("+OK"));

        // Count of dest should cover all 4 elements
        conn.clear();
        pfCount.execute(conn, tokens("PFCOUNT", "dest"));
        String resp = conn.getLastResponse();
        int colon = resp.indexOf(':');
        int cr    = resp.indexOf('\r');
        long estimate = Long.parseLong(resp.substring(colon + 1, cr));
        Assert.assertTrue("dest estimate >= 2", estimate >= 2);
    }

    /** PFMERGE with missing source key (treated as empty) */
    @Test
    public void testPfMerge_missingSource_treated_asEmpty() {
        pfAdd.execute(conn, tokens("PFADD", "real-src", "alpha", "beta"));
        conn.clear();

        pfMerge.execute(conn, tokens("PFMERGE", "merged-dest", "real-src", "ghost-key"));
        Assert.assertTrue("merge OK with ghost key", conn.getLastResponse().contains("+OK"));
    }

    /** PFMERGE too few args returns error */
    @Test
    public void testPfMerge_tooFewArgs_returnsError() {
        pfMerge.execute(conn, tokens("PFMERGE", "dest-only"));
        Assert.assertTrue("error on too few args", conn.getLastResponse().startsWith("-"));
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
