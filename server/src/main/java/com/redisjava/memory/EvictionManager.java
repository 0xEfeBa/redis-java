package com.redisjava.memory;

import com.redisjava.datastruct.Db;
import com.redisjava.stats.ServerStats;

/**
 * Proactive memory eviction manager.
 * <p>
 * Write commands call {@link #ensureMemory()} before allocating so that
 * the server stays within its configured memory budget. The implementation
 * is intentionally simple and single-threaded (event-loop safe).
 * </p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>If used memory is below the limit, return immediately (happy path).</li>
 *   <li>Run a small active-expire cycle to reclaim expired keys first.</li>
 *   <li>If still over limit, evict keys according to the configured policy.</li>
 *   <li>If eviction is not possible (no keys, NO_EVICTION policy), return false.</li>
 * </ol>
 */
public class EvictionManager {

    /** LRU sample size per eviction round (matches Redis default). */
    private static final int LRU_SAMPLES = 20;

    /** Max eviction attempts before giving up in one ensureMemory() call. */
    private static final int MAX_EVICTION_ATTEMPTS = 32;

    /** Expire samples per ensureMemory() call. */
    private static final int EXPIRE_SAMPLES = 32;

    /** Eviction is triggered when usage exceeds this fraction of max memory. */
    private static final float EVICTION_THRESHOLD = 0.95f;

    private static EvictionManager instance;

    private final MemoryManager memoryManager;
    private final EvictionPolicy policy;

    // ── Singleton lifecycle ──────────────────────────────────────────────

    /**
     * Initializes the singleton with the given memory manager and policy.
     *
     * @param memoryManager The memory manager to check usage against.
     * @param policy        The eviction policy to apply.
     */
    public static void init(MemoryManager memoryManager, EvictionPolicy policy) {
        instance = new EvictionManager(memoryManager, policy);
    }

    /**
     * Returns the singleton instance, or null if not yet initialized.
     */
    public static EvictionManager getInstance() {
        return instance;
    }

    // ── Construction ────────────────────────────────────────────────────

    private EvictionManager(MemoryManager memoryManager, EvictionPolicy policy) {
        this.memoryManager = memoryManager;
        this.policy = policy;
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Ensures there is enough free memory for an upcoming write.
     * <p>
     * Should be called at the start of any write command before allocating.
     * </p>
     *
     * @return {@code true} if memory is available (write may proceed),
     *         {@code false} if memory could not be freed (caller should
     *         return an OOM error to the client).
     */
    public boolean ensureMemory() {
        if (!isOverThreshold()) {
            return true;
        }

        Db db = Db.getInstanceSafe();
        if (db == null) return true;

        long nowMs = System.currentTimeMillis();

        // Step 1: reclaim expired keys first (free without eviction)
        int expired = db.activeExpireCycle(EXPIRE_SAMPLES, nowMs);
        if (expired > 0) {
            ServerStats.getInstance().keyExpired(expired);
            if (!isOverThreshold()) return true;
        }

        // Step 2: policy-based eviction
        if (policy == EvictionPolicy.NO_EVICTION) {
            return false;
        }

        int evicted = 0;
        for (int attempt = 0; attempt < MAX_EVICTION_ATTEMPTS; attempt++) {
            boolean didEvict = db.evictOneLru(LRU_SAMPLES, nowMs);
            if (!didEvict) break;
            evicted++;
            if (!isOverThreshold()) break;
        }

        if (evicted > 0) {
            ServerStats.getInstance().keyEvicted(evicted);
        }

        return !isOverThreshold();
    }

    /**
     * Returns the current eviction policy.
     *
     * @return The configured {@link EvictionPolicy}.
     */
    public EvictionPolicy getPolicy() {
        return policy;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Returns true when used memory exceeds the eviction threshold.
     */
    private boolean isOverThreshold() {
        if (memoryManager == null) return false;
        long maxMemory = memoryManager.getMaxMemory();
        if (maxMemory <= 0) return false;
        long used = memoryManager.getUsedMemory();
        return used >= (long) (maxMemory * EVICTION_THRESHOLD);
    }
}
