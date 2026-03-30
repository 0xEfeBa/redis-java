package com.redisjava.datastruct;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.memory.MemoryManager;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Incremental rehashing doğrulaması.
 * Dict'in ht0/ht1 çift tablo mekanizmasının doğru çalıştığını test eder.
 */
public class IncrementalRehashTest {

    private Dict dict;
    @BeforeEach

    public void setup() {
        dict = new Dict(new MemoryManager(4));
    }

    /** Load factor aşıldığında rehash başlamalı */
    @Test
    public void testRehash_triggeredAtLoadFactor() {
        // INITIAL_SIZE=16, LOAD_FACTOR=0.75 → 12 key'de rehash başlar
        for (int i = 0; i < 12; i++) {
            byte[] k = ("key" + i).getBytes();
            dict.put(k, RedisObject.string(new RString(("v" + i).getBytes(),
                    dict.getMemoryManager())));
        }
        // 13. key ile tetiklenmeli
        dict.put("trigger".getBytes(), RedisObject.string(
                new RString("x".getBytes(), dict.getMemoryManager())));

        assertTrue(dict.isRehashing(), "Rehash başlamalı");
    }

    /** Rehash sırasında tüm key'ler erişilebilir olmalı */
    @Test
    public void testRehash_dataAccessibleDuring() {
        int count = 50;
        for (int i = 0; i < count; i++) {
            byte[] k = ("rkey" + i).getBytes();
            dict.put(k, RedisObject.string(new RString(("rv" + i).getBytes(),
                    dict.getMemoryManager())));
        }
        // Rehash sırasında tümünü oku
        for (int i = 0; i < count; i++) {
            byte[] k = ("rkey" + i).getBytes();
            RedisObject val = dict.get(k);
            assertNotNull(val);
            RString str = (RString) val.getValue();
            assertEquals("rv" + i, new String(str.getBytes()));
        }
    }

    /** Rehash tamamlandıktan sonra tüm veri doğru */
    @Test
    public void testRehash_completesCorrectly() {
        int count = 1000;
        for (int i = 0; i < count; i++) {
            byte[] k = ("bigkey" + i).getBytes();
            dict.put(k, RedisObject.string(new RString(("bigval" + i).getBytes(),
                    dict.getMemoryManager())));
        }
        // Rehash'i tamamlatmak için birçok get yap
        for (int i = 0; i < count; i++) {
            dict.get(("bigkey" + i).getBytes());
        }
        // Artık rehash bitmeli
        assertFalse(dict.isRehashing(), "Rehash bitmeli");
        // Tüm key'ler hâlâ var
        for (int i = 0; i < count; i++) {
            assertNotNull(dict.get(("bigkey" + i).getBytes()));
        }
        assertEquals(count, dict.size());
    }

    /** Rehash sırasında DEL doğru çalışmalı */
    @Test
    public void testRehash_deletesDuringRehash() {
        for (int i = 0; i < 30; i++) {
            dict.put(("dk" + i).getBytes(), RedisObject.string(
                    new RString("v".getBytes(), dict.getMemoryManager())));
        }
        assertTrue(dict.isRehashing(), "Rehash başlamalı");

        // Ortadaki bir key sil
        dict.remove("dk10".getBytes());
        assertNull(dict.get("dk10".getBytes()));
        assertNotNull(dict.get("dk5".getBytes()));
    }

    /** Rehash sırasında size() her iki tablodan doğru toplamı göstermeli */
    @Test
    public void testSize_correctDuringRehash() {
        for (int i = 0; i < 20; i++) {
            dict.put(("sk" + i).getBytes(), RedisObject.string(
                    new RString("v".getBytes(), dict.getMemoryManager())));
        }
        assertEquals(20, dict.size());
        // Rehash sırasında da size değişmemeli
        if (dict.isRehashing()) {
            assertEquals(20, dict.size());
        }
    }

    /** Rehash sırasında güncelleme (PUT üzerine yaz) doğru çalışmalı */
    @Test
    public void testUpdate_duringRehash() {
        for (int i = 0; i < 20; i++) {
            dict.put(("uk" + i).getBytes(), RedisObject.string(
                    new RString("original".getBytes(), dict.getMemoryManager())));
        }
        // Rehash tetiklendi, şimdi bazılarını güncelle
        dict.put("uk5".getBytes(), RedisObject.string(
                new RString("updated".getBytes(), dict.getMemoryManager())));

        RedisObject val = dict.get("uk5".getBytes());
        assertNotNull(val);
        assertEquals("updated", new String(((RString) val.getValue()).getBytes()));
    }

    /** Tek operasyonun süresi makul olmalı (donma yok) */
    @Test
    public void testNoFreeze_singleOperationFast() {
        // 100.000 key ekle, her PUT'un süresi ölç
        MemoryManager bigMem = new MemoryManager(20);
        Dict bigDict = new Dict(bigMem);

        long maxNs = 0;
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            byte[] k = ("perfkey" + i).getBytes();
            long t0 = System.nanoTime();
            bigDict.put(k, RedisObject.string(new RString("v".getBytes(), bigMem)));
            long elapsed = System.nanoTime() - t0;
            if (elapsed > maxNs) maxNs = elapsed;
        }
        bigDict.clear();
        bigMem.shutdown();

        long maxMs = maxNs / 1_000_000;
        assertTrue(maxMs < 5, "Max tek operasyon < 5ms, actual=" + maxMs + "ms");
    }
}
