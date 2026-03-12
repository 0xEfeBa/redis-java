package com.redisjava.memory;

/**
 * Memory eviction policy when maxmemory is reached.
 * <p>
 * Mirrors the subset of Redis eviction policies used by this project.
 * </p>
 */
public enum EvictionPolicy {

    /**
     * Return an OOM error on write commands when memory is full.
     * No automatic eviction.
     */
    NO_EVICTION,

    /**
     * Evict any key using an approximate LRU algorithm.
     * Best for general-purpose caching (e.g. Event Discovery L1 cache).
     */
    ALLKEYS_LRU,

    /**
     * Evict only keys that have a TTL set, using approximate LRU.
     * Useful when only cache-like (expiring) keys should be removed.
     */
    VOLATILE_LRU;

    /**
     * Parses a policy string from configuration (case-insensitive).
     *
     * @param value Policy string (e.g. "allkeys-lru").
     * @return Matching policy, or {@link #NO_EVICTION} if unknown.
     */
    public static EvictionPolicy parse(String value) {
        if (value == null) return NO_EVICTION;
        switch (value.toLowerCase().trim()) {
            case "allkeys-lru":  return ALLKEYS_LRU;
            case "volatile-lru": return VOLATILE_LRU;
            default:             return NO_EVICTION;
        }
    }
}
