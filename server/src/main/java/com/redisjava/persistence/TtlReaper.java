package com.redisjava.persistence;

import com.redisjava.datastruct.Db;
import com.redisjava.stats.ServerStats;

/**
 * Active TTL Reaper — removes expired keys proactively.
 *
 * <h3>Design</h3>
 * <p>
 * Redis uses a two-tier expiry strategy:
 * <ol>
 *   <li><b>Lazy expiry</b>: checked on every GET/access — already handled by
 *       {@link com.redisjava.datastruct.Dict#get(byte[])}.</li>
 *   <li><b>Active expiry</b>: a background cycle that randomly samples keys
 *       with TTLs and removes those that have expired.</li>
 * </ol>
 * </p>
 *
 * <p>
 * This class implements the active side. It is intentionally called from the
 * single-threaded event loop via {@link #runCycle(long)} so no synchronisation
 * is needed. The reaper adjusts its sample size based on the ratio of expired
 * keys found: if the last cycle had a high hit rate it runs a larger sample
 * to keep expiry lag low (important for Ticketmaster 5-min reservation TTLs).
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   TtlReaper reaper = TtlReaper.getInstance();
 *   // Called from event loop every ~100ms:
 *   reaper.runCycle(System.currentTimeMillis());
 * }</pre>
 */
public class TtlReaper {

    // ── Tuning ─────────────────────────────────────────────────────────────

    /** Minimum keys to sample per cycle. */
    private static final int MIN_SAMPLES = 20;

    /** Maximum keys to sample per cycle (safety cap). */
    private static final int MAX_SAMPLES = 200;

    /**
     * If the expired-to-sampled ratio exceeds this threshold the next
     * cycle will use a larger sample.
     */
    private static final double SCALE_UP_THRESHOLD = 0.25;

    // ── Singleton ──────────────────────────────────────────────────────────

    private static final TtlReaper INSTANCE = new TtlReaper();

    public static TtlReaper getInstance() {
        return INSTANCE;
    }

    // ── State ──────────────────────────────────────────────────────────────

    private int currentSamples = MIN_SAMPLES;

    // ── API ────────────────────────────────────────────────────────────────

    /**
     * Runs one expiry cycle.
     *
     * @param nowMs Current wall-clock time in milliseconds.
     * @return Number of keys expired in this cycle.
     */
    public int runCycle(long nowMs) {
        Db db = Db.getInstanceSafe();
        if (db == null) return 0;

        int expired = db.activeExpireCycle(currentSamples, nowMs);

        if (expired > 0) {
            ServerStats.getInstance().keyExpired(expired);
        }

        // Adaptive sample sizing: if we found many expired keys, ramp up next
        // cycle; otherwise ramp down toward the minimum.
        double ratio = currentSamples > 0 ? (double) expired / currentSamples : 0.0;
        if (ratio > SCALE_UP_THRESHOLD) {
            currentSamples = Math.min(MAX_SAMPLES, currentSamples * 2);
        } else {
            currentSamples = Math.max(MIN_SAMPLES, currentSamples / 2);
        }

        return expired;
    }

    /**
     * Returns the current sample size (useful for monitoring / tests).
     *
     * @return Current sample count.
     */
    public int getCurrentSamples() {
        return currentSamples;
    }
}
