package com.redisjava.persistence;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.testutil.Assert;

public class TtlReaperTest {

    private TtlReaper reaper;
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(2));
        reaper = TtlReaper.getInstance();
    }
    @Test

    public void testRunCycle_emptyDb_returnsZero() {
        int expired = reaper.runCycle(System.currentTimeMillis());
        Assert.assertEquals(0, expired);
    }
    @Test

    public void testRunCycle_noExpiredKeys_returnsZero() {
        Db db = Db.getInstance();
        db.set("key1".getBytes(), "val1".getBytes(), System.currentTimeMillis() + 3_600_000);
        int expired = reaper.runCycle(System.currentTimeMillis());
        Assert.assertEquals(0, expired);
    }
    @Test

    public void testRunCycle_expiredKeys_removed() {
        Db db = Db.getInstance();
        long pastMs = System.currentTimeMillis() - 10_000;
        db.set("expA".getBytes(), "v".getBytes(), pastMs);
        db.set("expB".getBytes(), "v".getBytes(), pastMs);
        db.set("expC".getBytes(), "v".getBytes(), pastMs);
        long nowMs = System.currentTimeMillis();
        int total = 0;
        for (int c = 0; c < 5; c++) {
            total += reaper.runCycle(nowMs);
        }
        Assert.assertTrue("Should have expired 3 keys, got " + total, total >= 3);
    }
    @Test

    public void testAdaptiveSampling_initialSamplesIsMinimum() {
        Assert.assertEquals(20, reaper.getCurrentSamples());
    }
    @Test

    public void testAdaptiveSampling_noKeys_samplesDecreaseOrStay() {
        int before = reaper.getCurrentSamples();
        reaper.runCycle(System.currentTimeMillis());
        int after = reaper.getCurrentSamples();
        Assert.assertTrue("Samples should not increase when nothing expired", after <= before);
    }
}
