package com.redisjava.datastruct;

import com.redisjava.memory.MemoryManager;

/**
 * Hash table entry for Dict.
 * Uses separate chaining for collision resolution.
 */
class DictEntry {
    final RString key;
    RedisObject value;
    long expireAtMs;    // 0 = no expiry
    long lastAccessMs;
    DictEntry next;

    DictEntry(RString key, RedisObject value) {
        this.key = key;
        this.value = value;
        this.expireAtMs = 0;
        this.lastAccessMs = System.currentTimeMillis();
        this.next = null;
    }
}

/**
 * Redis-style hash table with incremental rehashing.
 * <p>
 * Maintains two tables (ht0 / ht1) during resize. Each mutating operation
 * migrates one bucket from ht0 → ht1 so no single operation ever blocks
 * for O(N) time. When rehashIndex reaches the end of ht0, ht1 becomes the
 * new primary table and ht0 is discarded.
 * </p>
 * <p>
 * During rehash, reads check both tables; writes always go to ht1.
 * </p>
 */
public class Dict {

    private static final int   INITIAL_SIZE      = 16;
    private static final float LOAD_FACTOR       = 0.75f;
    /** Max empty buckets skipped in one rehashStep call (prevents long stalls). */
    private static final int   MAX_EMPTY_VISITS  = 10;

    private static final long SIPHASH_KEY_0 = 0x0706050403020100L;
    private static final long SIPHASH_KEY_1 = 0x0f0e0d0c0b0a0908L;

    /** Primary table. During rehash this is the source. */
    private DictEntry[] ht0;
    /** Rehash target table; null when not rehashing. */
    private DictEntry[] ht1;
    /** Current bucket being migrated; -1 means no rehash in progress. */
    private int rehashIndex = -1;

    /** Total live entries across both tables. */
    private int size;

    private final MemoryManager memory;

    // ── Construction ────────────────────────────────────────────────────

    /**
     * Creates a new Dict with the given memory manager.
     *
     * @param memory The memory manager for off-heap allocation.
     */
    public Dict(MemoryManager memory) {
        this.memory = memory;
        this.ht0 = new DictEntry[INITIAL_SIZE];
        this.ht1 = null;
        this.size = 0;
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Stores a key-value pair with optional expiry.
     *
     * @param key        Key bytes.
     * @param value      Value object.
     * @param expireAtMs Absolute expiry timestamp in ms (0 = no expiry).
     */
    public void put(byte[] key, RedisObject value, long expireAtMs) {
        rehashStep();

        // After load-factor check, start rehash if needed (non-blocking)
        if (!isRehashing() && size >= ht0.length * LOAD_FACTOR) {
            startRehash();
        }

        // Always write to the active (newest) table
        DictEntry[] target = isRehashing() ? ht1 : ht0;
        int index = hash(key) & (target.length - 1);
        DictEntry entry = target[index];

        // If key already exists in either table, update in place
        DictEntry existing = findEntry(key);
        if (existing != null) {
            existing.value.free(memory);
            existing.value = value;
            existing.expireAtMs = expireAtMs;
            existing.lastAccessMs = System.currentTimeMillis();
            return;
        }

        // New entry — insert at head of chain in target table
        RString keyStr = new RString(key, memory);
        DictEntry newEntry = new DictEntry(keyStr, value);
        newEntry.expireAtMs = expireAtMs;
        newEntry.next = target[index];
        target[index] = newEntry;
        size++;
    }

    /**
     * Stores a key-value pair without expiry.
     *
     * @param key   Key bytes.
     * @param value Value object.
     */
    public void put(byte[] key, RedisObject value) {
        put(key, value, 0);
    }

    /**
     * Retrieves the value for a key, evicting it if expired.
     *
     * @param key Key bytes.
     * @return The RedisObject, or null if not found or expired.
     */
    public RedisObject get(byte[] key) {
        rehashStep();
        long now = System.currentTimeMillis();

        // Search ht0 first
        int idx0 = hash(key) & (ht0.length - 1);
        DictEntry prev = null;
        DictEntry entry = ht0[idx0];
        while (entry != null) {
            if (entry.key.equals(key)) {
                if (entry.expireAtMs > 0 && entry.expireAtMs <= now) {
                    removeEntryFrom(ht0, idx0, prev, entry);
                    return null;
                }
                entry.lastAccessMs = now;
                return entry.value;
            }
            prev = entry;
            entry = entry.next;
        }

        // If rehashing, also search ht1
        if (isRehashing()) {
            int idx1 = hash(key) & (ht1.length - 1);
            prev = null;
            entry = ht1[idx1];
            while (entry != null) {
                if (entry.key.equals(key)) {
                    if (entry.expireAtMs > 0 && entry.expireAtMs <= now) {
                        removeEntryFrom(ht1, idx1, prev, entry);
                        return null;
                    }
                    entry.lastAccessMs = now;
                    return entry.value;
                }
                prev = entry;
                entry = entry.next;
            }
        }
        return null;
    }

    /**
     * Removes a key from the dictionary.
     *
     * @param key Key bytes.
     * @return true if the key was found and removed.
     */
    public boolean remove(byte[] key) {
        rehashStep();

        if (removeFrom(ht0, key)) return true;
        if (isRehashing() && removeFrom(ht1, key)) return true;
        return false;
    }

    /**
     * Sets an absolute expiry timestamp for a key.
     *
     * @param key        Key bytes.
     * @param expireAtMs Absolute timestamp in ms.
     * @return true if the key exists and expiry was set.
     */
    public boolean setExpireAt(byte[] key, long expireAtMs) {
        DictEntry e = findEntry(key);
        if (e == null) return false;
        e.expireAtMs = expireAtMs;
        return true;
    }

    /**
     * Returns remaining TTL in milliseconds.
     *
     * @param key   Key bytes.
     * @param nowMs Current time in ms.
     * @return -2 if key absent/expired, -1 if no expiry, otherwise remaining ms.
     */
    public long ttlMs(byte[] key, long nowMs) {
        DictEntry e = findEntryRaw(key);
        if (e == null) return -2;
        if (e.expireAtMs > 0 && e.expireAtMs <= nowMs) {
            remove(key);
            return -2;
        }
        if (e.expireAtMs == 0) return -1;
        return Math.max(0, e.expireAtMs - nowMs);
    }

    /**
     * Clears expiry for a key.
     *
     * @param key Key bytes.
     * @return true if key exists and expiry was cleared.
     */
    public boolean clearExpire(byte[] key) {
        DictEntry e = findEntry(key);
        if (e == null) return false;
        e.expireAtMs = 0;
        return true;
    }

    /**
     * Clears expiry only if the key currently has one (Redis PERSIST semantics).
     *
     * @param key Key bytes.
     * @return true if key existed and had an expiry that was removed.
     */
    public boolean persistIfHasExpire(byte[] key) {
        DictEntry e = findEntry(key);
        if (e == null || e.expireAtMs == 0) return false;
        e.expireAtMs = 0;
        return true;
    }

    /**
     * Samples up to {@code samples} entries and removes expired ones.
     *
     * @param samples Max entries to inspect.
     * @param nowMs   Current time in ms.
     * @return Number of keys expired.
     */
    public int expireSample(int samples, long nowMs) {
        if (samples <= 0 || size == 0) return 0;

        int expired = 0;
        int inspected = 0;
        int idx = (int) (nowMs % ht0.length);

        // Scan ht0
        expired += scanForExpiry(ht0, idx, samples - expired, nowMs);
        inspected += Math.min(samples, ht0.length);

        // Scan ht1 if rehashing and budget remains
        if (isRehashing() && expired < samples) {
            int idx1 = (int) (nowMs % ht1.length);
            expired += scanForExpiry(ht1, idx1, samples - expired, nowMs);
        }

        return expired;
    }

    /**
     * Evicts one key using approximate LRU sampling.
     *
     * @param samples Number of random buckets to inspect.
     * @param nowMs   Current time in ms (used as PRNG seed).
     * @return true if a key was evicted.
     */
    public boolean evictOneLru(int samples, long nowMs) {
        if (samples <= 0 || size == 0) return false;

        DictEntry best = null;
        int bestIndex = -1;
        boolean bestInHt0 = true;

        long rnd = nowMs ^ 0x9E3779B97F4A7C15L;

        for (int i = 0; i < samples; i++) {
            rnd = xorshift64(rnd);

            // Alternate between ht0 and ht1 when rehashing for fairness
            DictEntry[] table = (isRehashing() && (i % 2 == 1)) ? ht1 : ht0;
            int mask = table.length - 1;
            int idx = ((int) rnd) & mask;

            DictEntry entry = table[idx];
            while (entry != null) {
                if (entry.expireAtMs == 0 || entry.expireAtMs > nowMs) {
                    if (best == null || entry.lastAccessMs < best.lastAccessMs) {
                        best = entry;
                        bestIndex = idx;
                        bestInHt0 = (table == ht0);
                    }
                }
                entry = entry.next;
            }
        }

        if (best == null) return false;

        DictEntry[] table = bestInHt0 ? ht0 : ht1;
        return removeFrom(table, best.key.getBytes());
    }

    /** @return Number of live entries. */
    public int size() {
        return size;
    }

    /** @return The memory manager used by this dictionary. */
    public MemoryManager getMemoryManager() {
        return memory;
    }

    /** @return true if an incremental rehash is currently in progress. */
    public boolean isRehashing() {
        return rehashIndex >= 0;
    }

    /**
     * Returns an iterable snapshot of all non-expired entries.
     * Not thread-safe; do not modify during iteration.
     */
    public Iterable<Entry> entries() {
        return () -> new java.util.Iterator<Entry>() {
            private int tableIdx   = 0;   // 0 = ht0, 1 = ht1
            private int bucketIdx  = 0;
            private DictEntry cur  = null;
            private DictEntry next = advance();

            private DictEntry advance() {
                // Continue from current chain
                if (cur != null && cur.next != null) {
                    cur = cur.next;
                    return cur;
                }
                // Move to next bucket in current table
                DictEntry[] active = (tableIdx == 0) ? ht0 : ht1;
                while (bucketIdx < active.length) {
                    DictEntry e = active[bucketIdx++];
                    if (e != null) { cur = e; return e; }
                }
                // Switch to ht1 if rehashing and not done
                if (tableIdx == 0 && isRehashing()) {
                    tableIdx = 1;
                    bucketIdx = 0;
                    active = ht1;
                    while (bucketIdx < active.length) {
                        DictEntry e = active[bucketIdx++];
                        if (e != null) { cur = e; return e; }
                    }
                }
                return null;
            }

            @Override public boolean hasNext() { return next != null; }

            @Override public Entry next() {
                if (next == null) throw new java.util.NoSuchElementException();
                final DictEntry captured = next;
                next = advance();
                return new Entry() {
                    @Override public RString getKey()       { return captured.key; }
                    @Override public RedisObject getValue() { return captured.value; }
                };
            }
        };
    }

    /**
     * Removes all entries and releases off-heap memory.
     */
    public void clear() {
        clearTable(ht0);
        if (ht1 != null) clearTable(ht1);
        ht0 = new DictEntry[INITIAL_SIZE];
        ht1 = null;
        rehashIndex = -1;
        size = 0;
    }

    // ── Entry interface (public iteration) ───────────────────────────────

    public interface Entry {
        RString getKey();
        RedisObject getValue();
    }

    // ── Incremental rehash internals ─────────────────────────────────────

    /**
     * Starts a new rehash: allocates ht1 at double the current capacity.
     */
    private void startRehash() {
        ht1 = new DictEntry[ht0.length * 2];
        rehashIndex = 0;
    }

    /**
     * Migrates one bucket from ht0 to ht1. Skips empty buckets (up to
     * MAX_EMPTY_VISITS) so callers incur at most a small, bounded cost.
     */
    private void rehashStep() {
        if (!isRehashing()) return;

        int emptyVisits = 0;
        while (rehashIndex < ht0.length) {
            if (ht0[rehashIndex] == null) {
                rehashIndex++;
                if (++emptyVisits >= MAX_EMPTY_VISITS) return;
                continue;
            }
            // Migrate all entries in this bucket
            DictEntry entry = ht0[rehashIndex];
            while (entry != null) {
                DictEntry next = entry.next;
                int idx1 = hash(entry.key.getBytes()) & (ht1.length - 1);
                entry.next = ht1[idx1];
                ht1[idx1] = entry;
                entry = next;
            }
            ht0[rehashIndex] = null;
            rehashIndex++;
            return; // one bucket per call
        }
        // Rehash complete
        finishRehash();
    }

    /** Replaces ht0 with ht1 and resets rehash state. */
    private void finishRehash() {
        ht0 = ht1;
        ht1 = null;
        rehashIndex = -1;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Finds an entry by key across both tables (respects lazy expiry).
     * Returns null if not found or expired.
     */
    private DictEntry findEntry(byte[] key) {
        long now = System.currentTimeMillis();
        DictEntry e = findInTable(ht0, key);
        if (e == null && isRehashing()) e = findInTable(ht1, key);
        if (e != null && e.expireAtMs > 0 && e.expireAtMs <= now) {
            remove(key);
            return null;
        }
        return e;
    }

    /**
     * Like findEntry but skips expiry check (used by ttlMs to avoid recursion).
     */
    private DictEntry findEntryRaw(byte[] key) {
        DictEntry e = findInTable(ht0, key);
        if (e == null && isRehashing()) e = findInTable(ht1, key);
        return e;
    }

    private DictEntry findInTable(DictEntry[] table, byte[] key) {
        int idx = hash(key) & (table.length - 1);
        DictEntry entry = table[idx];
        while (entry != null) {
            if (entry.key.equals(key)) return entry;
            entry = entry.next;
        }
        return null;
    }

    /** Removes an entry from a specific table by key. */
    private boolean removeFrom(DictEntry[] table, byte[] key) {
        int idx = hash(key) & (table.length - 1);
        DictEntry entry = table[idx];
        DictEntry prev = null;
        while (entry != null) {
            if (entry.key.equals(key)) {
                removeEntryFrom(table, idx, prev, entry);
                return true;
            }
            prev = entry;
            entry = entry.next;
        }
        return false;
    }

    private void removeEntryFrom(DictEntry[] table, int idx, DictEntry prev, DictEntry entry) {
        if (prev == null) {
            table[idx] = entry.next;
        } else {
            prev.next = entry.next;
        }
        entry.key.free(memory);
        entry.value.free(memory);
        size--;
    }

    /**
     * Scans up to {@code budget} entries starting from {@code startIdx},
     * removing expired ones.
     */
    private int scanForExpiry(DictEntry[] table, int startIdx, int budget, long nowMs) {
        int expired = 0;
        int inspected = 0;
        int bucketsScanned = 0;
        int idx = startIdx % table.length;

        // Stop when we have inspected 'budget' entries OR scanned the entire table,
        // whichever comes first.  Without the table-length guard, an empty (or
        // fully-expired) table would cause an infinite loop because 'inspected' is
        // only incremented per entry, not per bucket.
        while (inspected < budget && bucketsScanned < table.length) {
            DictEntry entry = table[idx];
            DictEntry prev = null;
            while (entry != null && inspected < budget) {
                inspected++;
                DictEntry next = entry.next;
                if (entry.expireAtMs > 0 && entry.expireAtMs <= nowMs) {
                    removeEntryFrom(table, idx, prev, entry);
                    expired++;
                    entry = next;
                    continue;
                }
                prev = entry;
                entry = next;
            }
            idx = (idx + 1) % table.length;
            bucketsScanned++;
        }
        return expired;
    }

    private void clearTable(DictEntry[] table) {
        for (DictEntry entry : table) {
            while (entry != null) {
                DictEntry next = entry.next;
                entry.key.free(memory);
                entry.value.free(memory);
                entry = next;
            }
        }
    }

    private int hash(byte[] key) {
        long h = SipHash.hash24(key, SIPHASH_KEY_0, SIPHASH_KEY_1);
        return (int) (h ^ (h >>> 32));
    }

    private static long xorshift64(long x) {
        x ^= (x << 13);
        x ^= (x >>> 7);
        x ^= (x << 17);
        return x;
    }
}
