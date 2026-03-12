package com.redisjava.datastruct;

/**
 * HyperLogLog cardinality estimator.
 *
 * <h3>Algorithm</h3>
 * <p>
 * Uses the HyperLogLog++ algorithm with:
 * <ul>
 *   <li>64-bit hash (64-bit Murmur3-like mix)</li>
 *   <li>b = 14 bits → 2^14 = 16 384 registers (Redis default)</li>
 *   <li>64-bit registers (stores leading-zero count in lower 6 bits)</li>
 *   <li>Small-range bias correction using linear counting when fewer
 *       than ~3/4 of registers are non-zero</li>
 * </ul>
 * </p>
 *
 * <h3>Accuracy</h3>
 * <p>
 * Expected error rate ≈ 1.04 / √(m) where m = 2^b.
 * With b = 14: error ≈ 1.04 / √16384 ≈ 0.81%.
 * </p>
 *
 * <h3>Supported operations</h3>
 * <ul>
 *   <li>{@link #add(byte[])}   – adds an element, returns true if cardinality changed</li>
 *   <li>{@link #count()}       – estimates cardinality</li>
 *   <li>{@link #merge(HyperLogLog)} – union of two HLL structures</li>
 * </ul>
 */
public class HyperLogLog {

    // ── Constants ──────────────────────────────────────────────────────────

    /** Register precision bits (p). 14 → 16 384 registers (Redis default). */
    private static final int P = 14;

    /** Number of registers: m = 2^p. */
    private static final int M = 1 << P;

    /** Mask to extract register index from hash. */
    private static final long INDEX_MASK = M - 1;

    /** Bias correction alpha_m for m ≥ 128. */
    private static final double ALPHA_M;

    static {
        // alpha_m ≈ 0.7213 / (1 + 1.079/m) for m ≥ 128
        ALPHA_M = 0.7213 / (1.0 + 1.079 / M);
    }

    // ── State ──────────────────────────────────────────────────────────────

    /** Registers stored as byte[] — sufficient for 64-bit hashes (max ρ = 64). */
    private final byte[] registers;

    // ── Construction ──────────────────────────────────────────────────────

    public HyperLogLog() {
        this.registers = new byte[M];
    }

    /** Copy constructor (used by merge). */
    private HyperLogLog(byte[] registers) {
        this.registers = registers.clone();
    }

    // ── API ────────────────────────────────────────────────────────────────

    /**
     * Adds an element.
     *
     * @param element Element bytes.
     * @return true if the estimated cardinality changed.
     */
    public boolean add(byte[] element) {
        long hash = hash64(element);

        // Extract register index from lower p bits
        int j = (int) (hash & INDEX_MASK);

        // rho = position of first 1-bit in the remaining bits (1-based)
        long w = hash >>> P;
        byte rho = (byte) (Long.numberOfLeadingZeros((w << P) | INDEX_MASK) + 1);
        // Cap at 64 - P + 1 to avoid overflow
        if (rho > 64 - P + 1) rho = (byte) (64 - P + 1);

        if (registers[j] < rho) {
            registers[j] = rho;
            return true;
        }
        return false;
    }

    /**
     * Returns the estimated cardinality.
     *
     * @return Estimated number of distinct elements.
     */
    public long count() {
        // Raw HyperLogLog estimate: alpha * m^2 * sum(2^-M[j])^-1
        double sum = 0.0;
        int zeros = 0;
        for (byte reg : registers) {
            sum += Math.pow(2, -reg);
            if (reg == 0) zeros++;
        }

        double estimate = ALPHA_M * M * M / sum;

        // Small range correction: linear counting
        if (estimate <= 2.5 * M && zeros > 0) {
            estimate = M * Math.log((double) M / zeros);
        }

        return Math.round(estimate);
    }

    /**
     * Merges another HyperLogLog into this one (union operation).
     * Takes the max of each register pair.
     *
     * @param other The other HLL.
     */
    public void merge(HyperLogLog other) {
        for (int i = 0; i < M; i++) {
            if (other.registers[i] > this.registers[i]) {
                this.registers[i] = other.registers[i];
            }
        }
    }

    /**
     * Returns a copy of this HyperLogLog.
     *
     * @return Deep copy.
     */
    public HyperLogLog copy() {
        return new HyperLogLog(registers);
    }

    /**
     * @return Number of registers.
     */
    public int getRegisterCount() {
        return M;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * 64-bit Murmur3-inspired hash for byte arrays.
     */
    static long hash64(byte[] data) {
        long h = 0xDEAD_BEEF_CAFE_BABEL;

        for (byte b : data) {
            h ^= (b & 0xFFL);
            h = Long.rotateLeft(h, 23);
            h = h * 0x2545_F491_4F6C_DD1DL ^ 0x1234_5678_9ABC_DEF0L;
        }

        // Final mix
        h ^= (h >>> 33);
        h *= 0xFF51_AFD7_ED55_8CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CE_B9FE_1A85_EC53L;
        h ^= (h >>> 33);
        return h;
    }
}
