package com.redisjava.datastruct;

import com.redisjava.memory.MemoryManager;

/**
 * Redis database - main key-value store.
 * <p>
 * Uses Dict for storage with off-heap RString keys and typed values.
 * Single database instance (no SELECT support for simplicity).
 * </p>
 */
public class Db {

    private static Db instance;

    private final Dict dict;

    /**
     * Initializes the database with a memory manager.
     * Must be called before getInstance().
     *
     * @param memory The memory manager for off-heap allocation.
     */
    public static void init(MemoryManager memory) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new Db(memory);
    }

    /**
     * Gets the singleton database instance.
     *
     * @return The database instance.
     * @throws IllegalStateException if init() was not called.
     */
    public static Db getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Db not initialized. Call Db.init(MemoryManager) first.");
        }
        return instance;
    }

    /**
     * Helper for tests to check if initialized.
     */
    public static Db getInstanceSafe() {
        return instance;
    }

    private Db(MemoryManager memory) {
        this.dict = new Dict(memory);
    }

    /**
     * SET command: stores a key-value pair with optional expiry.
     *
     * @param key        The key bytes.
     * @param value      The value wrapper.
     * @param expireAtMs Absolute timestamp in ms for expiry (0 = no expiry).
     */
    public void set(RString key, RedisObject value, long expireAtMs) {
        dict.put(key.getBytes(), value, expireAtMs);
    }

    /**
     * SET command: stores a key-value pair.
     *
     * @param key   The key bytes.
     * @param value The value wrapper.
     */
    public void set(RString key, RedisObject value) {
        set(key, value, 0);
    }

    /**
     * Stores a typed RedisObject under a raw byte key (no TTL).
     * Used by LIST/ZSET/etc. commands that manage their own object lifecycle.
     *
     * @param key   Raw key bytes.
     * @param value The RedisObject to store.
     */
    public void set(byte[] key, RedisObject value) {
        dict.put(key, value, 0);
    }

    /**
     * Deprecated: Use set(RString, RedisObject)
     */
    /**
     * Deprecated: Use set(RString, RedisObject)
     */
    public void set(byte[] key, byte[] value) {
        set(key, value, 0);
    }

    /**
     * Helper wrapper for byte arrays with expiry.
     *
     * @param key        Key bytes.
     * @param value      Value bytes.
     * @param expireAtMs Absolute timestamp in ms for expiry (0 = no expiry).
     */
    public void set(byte[] key, byte[] value, long expireAtMs) {
        RString valueStr = new RString(value, dict.getMemoryManager());
        dict.put(key, RedisObject.string(valueStr), expireAtMs);
    }

    /**
     * GET command: retrieves object for a key.
     *
     * @param key The key bytes.
     * @return The RedisObject, or null if not found.
     */
    public RedisObject get(RString key) {
        return dict.get(key.getBytes());
    }

    /**
     * Deprecated: Use get(RString)
     */
    public byte[] get(byte[] key) {
        RedisObject value = dict.get(key);
        if (value == null || value.getType() != RedisObject.Type.STRING) {
            return null;
        }
        RString str = (RString) value.getValue();
        return str.getBytes();
    }

    /**
     * Gets the raw object for a key.
     *
     * @param key The key bytes.
     * @return The RedisObject, or null if not found.
     */
    public RedisObject getObject(byte[] key) {
        return dict.get(key);
    }

    /**
     * Sets an absolute expiry time for the key.
     *
     * @param key        Key bytes.
     * @param expireAtMs Absolute timestamp in ms since epoch.
     * @return true if key exists and expiry was set.
     */
    public boolean expireAt(byte[] key, long expireAtMs) {
        return dict.setExpireAt(key, expireAtMs);
    }

    /**
     * Removes expiry from a key (persist).
     *
     * @param key Key bytes.
     * @return true if key exists and expiry was cleared.
     */
    public boolean persist(byte[] key) {
        return dict.clearExpire(key);
    }

    /**
     * Redis PERSIST semantics: return true only if the timeout was removed.
     *
     * @param key Key bytes.
     * @return true if key exists and had an expiry that was removed.
     */
    public boolean persistIfHasExpire(byte[] key) {
        return dict.persistIfHasExpire(key);
    }

    /**
     * Redis TTL semantics (seconds):
     * -2 if key does not exist
     * -1 if key exists but has no expiry
     * >= 0 remaining time in seconds (can be 0)
     */
    public long ttlSeconds(byte[] key) {
        long nowMs = System.currentTimeMillis();
        long ttlMs = dict.ttlMs(key, nowMs);
        if (ttlMs == -2 || ttlMs == -1) {
            return ttlMs;
        }
        return ttlMs / 1000L;
    }

    /**
     * Evicts one key using approximate LRU sampling.
     *
     * @param samples Number of samples.
     * @param nowMs   Current time in ms.
     * @return true if a key was evicted.
     */
    public boolean evictOneLru(int samples, long nowMs) {
        return dict.evictOneLru(samples, nowMs);
    }

    /**
     * Active expire cycle: samples keys and deletes expired ones.
     *
     * @param samples How many entries to inspect.
     * @param nowMs   Current time in ms.
     * @return Number of expired keys removed.
     */
    public int activeExpireCycle(int samples, long nowMs) {
        return dict.expireSample(samples, nowMs);
    }

    /**
     * DEL command: removes a key.
     *
     * @param key The key bytes.
     * @return true if key was found and removed.
     */
    public boolean del(byte[] key) {
        return dict.remove(key);
    }

    /**
     * @return Number of keys in the database.
     */
    public int size() {
        return dict.size();
    }

    /**
     * @return Total off-heap memory used in bytes.
     */
    public long getUsedMemory() {
        return dict.getMemoryManager().getUsedMemory();
    }

    /**
     * @return The memory manager used by this database.
     */
    public MemoryManager getMemoryManager() {
        return dict.getMemoryManager();
    }

    /**
     * Clears all data from the database.
     */
    public void clear() {
        dict.clear();
    }

    /**
     * FLUSHALL command: clears all data.
     */
    public void flushAll() {
        clear();
    }

    /**
     * Shuts down the database and releases all memory.
     * Note: This invalidates the singleton instance.
     */
    public void shutdown() {
        clear();
        dict.getMemoryManager().shutdown();
        instance = null;
    }
}
