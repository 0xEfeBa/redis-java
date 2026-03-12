package com.redisjava.datastruct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Probabilistic skip-list for the Redis Sorted Set (ZSET) data structure.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Maximum level: 32 (enough for 2^32 elements).</li>
 *   <li>Level-up probability: 0.25 (lower than Redis's 0.25, same trade-off).</li>
 *   <li>Node members are raw {@code byte[]}; scores are {@code double}.</li>
 *   <li>All operations are O(log N) expected.</li>
 * </ul>
 *
 * <h3>Supported operations</h3>
 * <ul>
 *   <li>ZADD  – insert/update (score, member), returns 1 if new, 0 if updated</li>
 *   <li>ZREM  – remove by member</li>
 *   <li>ZSCORE – get score of member</li>
 *   <li>ZRANK  – 0-based rank of member (ascending by score)</li>
 *   <li>ZRANGE – slice [start, stop] (0-based, ascending by score)</li>
 *   <li>ZCARD  – number of elements</li>
 * </ul>
 */
public class SkipList {

    // ── Constants ─────────────────────────────────────────────────────────

    private static final int MAX_LEVEL = 32;
    private static final double P = 0.25;

    // ── Inner types ───────────────────────────────────────────────────────

    /** A node in the skip-list. */
    static final class Node {
        final byte[] member;
        double score;
        final Node[] forward; // forward[i] = next node at level i

        Node(byte[] member, double score, int level) {
            this.member  = member;
            this.score   = score;
            this.forward = new Node[level];
        }
    }

    // ── State ─────────────────────────────────────────────────────────────

    /** Sentinel header node (member = null, score = -∞). */
    private final Node header;

    /** Current maximum level in use (1-based). */
    private int level;

    /** Total number of elements (excluding header). */
    private int size;

    private final Random rng = new Random();

    // ── Construction ──────────────────────────────────────────────────────

    public SkipList() {
        this.header = new Node(null, Double.NEGATIVE_INFINITY, MAX_LEVEL);
        this.level  = 1;
        this.size   = 0;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Inserts or updates a (score, member) pair.
     *
     * @param score  Score.
     * @param member Member bytes (not null).
     * @return 1 if a new element was inserted, 0 if an existing one was updated.
     */
    public int zadd(double score, byte[] member) {
        // Scan level 0 to find if the member already exists (members are unique).
        // We must check by member identity, not by (score, member) pair, because the
        // caller may supply a different score for the same member.
        Node cur = header.forward[0];
        while (cur != null) {
            if (Arrays.equals(cur.member, member)) {
                // Member exists — update score (remove and reinsert if changed)
                if (cur.score != score) {
                    zrem(member);
                    insertNew(score, member);
                }
                return 0;
            }
            cur = cur.forward[0];
        }
        insertNew(score, member);
        return 1;
    }

    /**
     * Removes the element with the given member.
     *
     * @param member Member bytes.
     * @return true if the element was found and removed.
     */
    public boolean zrem(byte[] member) {
        @SuppressWarnings("unchecked")
        Node[] update = new Node[MAX_LEVEL];
        Node x = header;

        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && !Arrays.equals(x.forward[i].member, member)
                    && cmpByMember(x.forward[i], member) < 0) {
                x = x.forward[i];
            }
            update[i] = x;
        }

        // Locate the node to delete
        Node target = null;
        Node cur = header.forward[0];
        while (cur != null) {
            if (Arrays.equals(cur.member, member)) {
                target = cur;
                break;
            }
            cur = cur.forward[0];
        }

        if (target == null) return false;

        // Rebuild update array pointing to target's predecessors
        x = header;
        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && x.forward[i] != target
                    && cmp(x.forward[i], target.score, target.member) < 0) {
                x = x.forward[i];
            }
            update[i] = x;
        }

        for (int i = 0; i < level; i++) {
            if (update[i].forward[i] != target) break;
            update[i].forward[i] = target.forward[i];
        }

        // Reduce level if top levels are empty
        while (level > 1 && header.forward[level - 1] == null) {
            level--;
        }
        size--;
        return true;
    }

    /**
     * Returns the score of a member, or {@link Double#NaN} if not found.
     *
     * @param member Member bytes.
     * @return Score or {@code Double.NaN}.
     */
    public double zscore(byte[] member) {
        Node cur = header.forward[0];
        while (cur != null) {
            if (Arrays.equals(cur.member, member)) {
                return cur.score;
            }
            cur = cur.forward[0];
        }
        return Double.NaN;
    }

    /**
     * Returns the 0-based rank of a member (ascending score order),
     * or -1 if not found.
     *
     * @param member Member bytes.
     * @return Rank (0-based) or -1.
     */
    public long zrank(byte[] member) {
        long rank = 0;
        Node cur = header.forward[0];
        while (cur != null) {
            if (Arrays.equals(cur.member, member)) {
                return rank;
            }
            rank++;
            cur = cur.forward[0];
        }
        return -1;
    }

    /**
     * Returns an array of (member, score) pairs in the range [start, stop]
     * (0-based, ascending score order, negative indices supported).
     *
     * @param start Start index.
     * @param stop  Stop index.
     * @return Array of ZEntry objects (never null).
     */
    public ZEntry[] zrange(long start, long stop) {
        if (size == 0) return new ZEntry[0];

        long s = start < 0 ? Math.max(0, size + start) : start;
        long e = stop  < 0 ? size + stop                 : stop;

        if (s >= size || e < 0 || s > e) return new ZEntry[0];
        e = Math.min(e, size - 1);

        int count = (int) (e - s + 1);
        ZEntry[] result = new ZEntry[count];

        long idx = 0;
        int ri = 0;
        Node cur = header.forward[0];
        while (cur != null && ri < count) {
            if (idx >= s) {
                result[ri++] = new ZEntry(cur.member, cur.score);
            }
            idx++;
            cur = cur.forward[0];
        }
        return result;
    }

    /**
     * Returns the number of elements in the sorted set.
     *
     * @return Element count.
     */
    public int zcard() {
        return size;
    }

    /**
     * ZADD NX — sadece member yoksa ekle.
     *
     * @return 1 eklendiyse, 0 zaten varsa.
     */
    public int zaddNx(double score, byte[] member) {
        Node cur = header.forward[0];
        while (cur != null) {
            if (Arrays.equals(cur.member, member)) return 0; // zaten var, ekleme
            cur = cur.forward[0];
        }
        insertNew(score, member);
        return 1;
    }

    /**
     * ZADD XX — sadece member varsa güncelle.
     *
     * @return 1 güncellendiyse, 0 bulunamadıysa.
     */
    public int zaddXx(double score, byte[] member) {
        Node cur = header.forward[0];
        while (cur != null) {
            if (Arrays.equals(cur.member, member)) {
                if (cur.score != score) { zrem(member); insertNew(score, member); }
                return 1;
            }
            cur = cur.forward[0];
        }
        return 0; // yok, ekleme
    }

    /**
     * ZRANGEBYSCORE min max [LIMIT offset count]
     * Double.NEGATIVE_INFINITY / POSITIVE_INFINITY ile açık aralık.
     *
     * @param offset LIMIT başlangıcı (0 = baştan)
     * @param count  LIMIT adedi  (-1 = hepsi)
     * @return ZEntry dizisi, boş olabilir ama null değil.
     */
    public ZEntry[] zrangeByScore(double min, double max, long offset, long count) {
        List<ZEntry> result = new ArrayList<>();
        long skipped = 0;
        Node cur = header.forward[0];
        while (cur != null) {
            if (cur.score > max) break;
            if (cur.score >= min) {
                if (skipped < offset) { skipped++; }
                else {
                    result.add(new ZEntry(cur.member, cur.score));
                    if (count >= 0 && result.size() >= count) break;
                }
            }
            cur = cur.forward[0];
        }
        return result.toArray(new ZEntry[0]);
    }

    /**
     * ZREMRANGEBYSCORE min max
     *
     * @return silinen eleman sayısı.
     */
    public long zremrangeByScore(double min, double max) {
        List<byte[]> toRemove = new ArrayList<>();
        Node cur = header.forward[0];
        while (cur != null) {
            if (cur.score > max) break;
            if (cur.score >= min) toRemove.add(cur.member);
            cur = cur.forward[0];
        }
        for (byte[] m : toRemove) zrem(m);
        return toRemove.size();
    }

    /**
     * ZCOUNT min max — score aralığındaki eleman sayısı.
     */
    public long zcount(double min, double max) {
        long count = 0;
        Node cur = header.forward[0];
        while (cur != null) {
            if (cur.score > max) break;
            if (cur.score >= min) count++;
            cur = cur.forward[0];
        }
        return count;
    }

    // ── Entry value object ────────────────────────────────────────────────

    /** Immutable (member, score) pair returned by zrange(). */
    public static final class ZEntry {
        public final byte[] member;
        public final double score;

        public ZEntry(byte[] member, double score) {
            this.member = member;
            this.score  = score;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void insertNew(double score, byte[] member) {
        @SuppressWarnings("unchecked")
        Node[] update = new Node[MAX_LEVEL];
        Node x = header;

        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && cmp(x.forward[i], score, member) < 0) {
                x = x.forward[i];
            }
            update[i] = x;
        }

        int newLevel = randomLevel();
        if (newLevel > level) {
            for (int i = level; i < newLevel; i++) {
                update[i] = header;
            }
            level = newLevel;
        }

        Node newNode = new Node(member, score, newLevel);
        for (int i = 0; i < newLevel; i++) {
            newNode.forward[i] = update[i].forward[i];
            update[i].forward[i] = newNode;
        }
        size++;
    }

    /**
     * Compare (node.score, node.member) vs (score, member) lexicographically
     * by score first, then by member bytes.
     */
    private static int cmp(Node node, double score, byte[] member) {
        if (node.score != score) {
            return Double.compare(node.score, score);
        }
        return Arrays.compare(node.member, member);
    }

    private static int cmpByMember(Node node, byte[] member) {
        return Arrays.compare(node.member, member);
    }

    private int randomLevel() {
        int lvl = 1;
        while (lvl < MAX_LEVEL && rng.nextDouble() < P) {
            lvl++;
        }
        return lvl;
    }
}
