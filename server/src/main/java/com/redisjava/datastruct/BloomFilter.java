package com.redisjava.datastruct;

/**
 * Space-efficient probabilistic data structure for set membership tests.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Bit array backed by a {@code long[]}.</li>
 *   <li>Uses {@code k} independent hash functions derived from two base hashes
 *       (double-hashing technique: {@code h_i = h1 + i*h2 mod m}).</li>
 *   <li>Configurable false-positive rate ({@code errorRate}) and expected
 *       capacity ({@code expectedItems}).</li>
 * </ul>
 *
 * <h3>Supported operations</h3>
 * <ul>
 *   <li>{@link #add(byte[])}    – inserts a member (always returns true)</li>
 *   <li>{@link #mightContain(byte[])} – tests membership</li>
 *   <li>{@link #count()}        – approximate count of inserted items</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   BloomFilter bf = new BloomFilter(1000, 0.01);
 *   bf.add("foo".getBytes());
 *   bf.mightContain("foo".getBytes()); // true (no false negatives)
 *   bf.mightContain("bar".getBytes()); // false with ~99% probability
 * }</pre>
 */
public class BloomFilter {

    // ── Constants ──────────────────────────────────────────────────────────

    /** Minimum error rate (0.0001 = 0.01%). */
    private static final double MIN_ERROR_RATE = 0.0001;

    /** Maximum error rate (0.5 = 50%). */
    private static final double MAX_ERROR_RATE = 0.5;

    // ── State ──────────────────────────────────────────────────────────────

    /** Bit array stored as long[] (each long = 64 bits). */
    private final long[] bits;

    /** Total number of bits. */
    private final int m;

    /** Number of hash functions. */
    private final int k;

    /** Approximate number of items inserted. */
    private long insertCount = 0;

    // ── Construction ──────────────────────────────────────────────────────

    /**
     * Creates a Bloom filter with the given capacity and error rate.
     *
     * @param expectedItems Expected number of items to insert.
     * @param errorRate     Desired false-positive rate (e.g., 0.01 = 1%).
     * @throws IllegalArgumentException if parameters are invalid.
     */
    public BloomFilter(int expectedItems, double errorRate) {
        if (expectedItems <= 0) {
            throw new IllegalArgumentException("expectedItems must be > 0");
        }
        if (errorRate < MIN_ERROR_RATE || errorRate > MAX_ERROR_RATE) {
            throw new IllegalArgumentException(
                    "errorRate must be in [" + MIN_ERROR_RATE + ", " + MAX_ERROR_RATE + "]");
        }

        // Optimal bit count: m = -n * ln(p) / (ln(2))^2
        double ln2 = Math.log(2);
        long optimalBits = (long) Math.ceil(-expectedItems * Math.log(errorRate) / (ln2 * ln2));
        // Align to 64-bit words and cap at a sane maximum
        this.m = (int) Math.min(optimalBits, Integer.MAX_VALUE - 64);
        this.bits = new long[(m + 63) / 64];

        // Optimal hash count: k = (m / n) * ln(2)
        this.k = Math.max(1, (int) Math.round(((double) m / expectedItems) * ln2));
    }

    /**
     * Creates a Bloom filter with explicit bit count and hash count.
     * Used internally or for precise control.
     *
     * @param bitCount  Number of bits.
     * @param hashCount Number of hash functions.
     */
    public BloomFilter(int bitCount, int hashCount, boolean explicit) {
        if (bitCount <= 0 || hashCount <= 0) {
            throw new IllegalArgumentException("bitCount and hashCount must be > 0");
        }
        this.m    = bitCount;
        this.bits = new long[(bitCount + 63) / 64];
        this.k    = hashCount;
    }

    // ── API ────────────────────────────────────────────────────────────────

    /**
     * Inserts a member into the filter.
     *
     * @param member Member bytes.
     */
    public void add(byte[] member) {
        long[] hashes = computeHashes(member);
        for (int i = 0; i < k; i++) {
            int bit = bitIndex(hashes, i);
            bits[bit >>> 6] |= (1L << (bit & 63));
        }
        insertCount++;
    }

    /**
     * Tests whether a member might be in the set.
     *
     * <p>Returns {@code false} if the member is definitely not in the set,
     * {@code true} if it might be (with probability ≤ configured error rate).
     *
     * @param member Member bytes.
     * @return true if member might be present, false if definitely absent.
     */
    public boolean mightContain(byte[] member) {
        long[] hashes = computeHashes(member);
        for (int i = 0; i < k; i++) {
            int bit = bitIndex(hashes, i);
            if ((bits[bit >>> 6] & (1L << (bit & 63))) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return Approximate number of items inserted.
     */
    public long count() {
        return insertCount;
    }

    /**
     * @return Total number of bits in the filter.
     */
    public int getBitCount() {
        return m;
    }

    /**
     * @return Number of hash functions.
     */
    public int getHashCount() {
        return k;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Computes two independent 64-bit hashes from member bytes.
     * Uses Murmur3-like mixing for good distribution.
     *
     * @return Two hashes: [h1, h2].
     */
    private static long[] computeHashes(byte[] data) {
        long h1 = 0x9368_5B95_D2A5_9677L;
        long h2 = 0xC4CF_D3E9_1C4B_3C29L;

        for (byte b : data) {
            h1 ^= b;
            h1 = Long.rotateLeft(h1, 31);
            h1 = h1 * 0x517C_C1B7_2722_0A95L + 0x0E35_12E0_2C5E_8C9BL;
            h2 ^= b;
            h2 = Long.rotateLeft(h2, 17);
            h2 = h2 * 0xBF58_476D_1CE4_E5B9L + 0x94D0_49BB_1331_11EBL;
        }

        // Final avalanche
        h1 ^= (h1 >>> 33);
        h1 *= 0xFF51_AFD7_ED55_8CCDL;
        h1 ^= (h1 >>> 33);
        h1 *= 0xC4CE_B9FE_1A85_EC53L;
        h1 ^= (h1 >>> 33);

        h2 ^= (h2 >>> 33);
        h2 *= 0xFF51_AFD7_ED55_8CCDL;
        h2 ^= (h2 >>> 33);
        h2 *= 0xC4CE_B9FE_1A85_EC53L;
        h2 ^= (h2 >>> 33);

        return new long[]{h1, h2};
    }

    /**
     * Computes the i-th bit index using double hashing:
     * {@code (h1 + i * h2) mod m}.
     */
    private int bitIndex(long[] hashes, int i) {
        long raw = (hashes[0] + (long) i * hashes[1]);
        // Keep positive
        int bit = (int) ((raw & Long.MAX_VALUE) % m);
        return bit;
    }
}
