package com.redisjava.datastruct;

import com.redisjava.memory.MemoryManager;

/**
 * Wrapper for typed Redis values stored in the database.
 */
public final class RedisObject {

    public enum Type {
        STRING,
        LIST,
        HASH,
        SET,
        ZSET,
        BLOOM,
        HLL
    }

    private final Type type;
    private final Object value;

    private RedisObject(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static RedisObject string(RString value) {
        return new RedisObject(Type.STRING, value);
    }

    public static RedisObject hash(Dict value) {
        return new RedisObject(Type.HASH, value);
    }

    public static RedisObject list(RedisList value) {
        return new RedisObject(Type.LIST, value);
    }

    public static RedisObject zset(SkipList value) {
        return new RedisObject(Type.ZSET, value);
    }

    public static RedisObject bloom(BloomFilter value) {
        return new RedisObject(Type.BLOOM, value);
    }

    public static RedisObject hll(HyperLogLog value) {
        return new RedisObject(Type.HLL, value);
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public boolean isHash() {
        return type == Type.HASH;
    }

    public boolean isList() {
        return type == Type.LIST;
    }

    public Dict asHash() {
        if (!isHash()) {
            throw new IllegalStateException("Not a hash");
        }
        return (Dict) value;
    }

    public RedisList asList() {
        if (!isList()) {
            throw new IllegalStateException("Not a list");
        }
        return (RedisList) value;
    }

    public boolean isZset() {
        return type == Type.ZSET;
    }

    public SkipList asZset() {
        if (!isZset()) {
            throw new IllegalStateException("Not a zset");
        }
        return (SkipList) value;
    }

    public boolean isBloom() {
        return type == Type.BLOOM;
    }

    public BloomFilter asBloom() {
        if (!isBloom()) {
            throw new IllegalStateException("Not a bloom filter");
        }
        return (BloomFilter) value;
    }

    public boolean isHll() {
        return type == Type.HLL;
    }

    public HyperLogLog asHll() {
        if (!isHll()) {
            throw new IllegalStateException("Not a HyperLogLog");
        }
        return (HyperLogLog) value;
    }

    public void free(MemoryManager memory) {
        if (value instanceof RString) {
            ((RString) value).free(memory);
        } else if (value instanceof ZipList) {
            ((ZipList) value).free(memory);
        } else if (value instanceof Dict) {
            ((Dict) value).clear(); // Dict clears its own entries
        }
    }
}
