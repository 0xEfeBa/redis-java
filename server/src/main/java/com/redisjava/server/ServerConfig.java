package com.redisjava.server;

import com.redisjava.memory.EvictionPolicy;

/**
 * Redis server configuration.
 * <p>
 * Holds all configurable parameters for the server.
 * Values can be overridden via command line arguments.
 * </p>
 */
public class ServerConfig {

    /** Default Redis port */
    public static final int DEFAULT_PORT = 6379;

    /** Default max memory (64MB) */
    public static final long DEFAULT_MAX_MEMORY = 64 * 1024 * 1024;

    /** Default idle timeout (60 seconds) */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 60_000;

    /** Default eviction policy */
    public static final EvictionPolicy DEFAULT_EVICTION_POLICY = EvictionPolicy.ALLKEYS_LRU;

    private int port;
    private long maxMemory;
    private long idleTimeoutMs;
    private EvictionPolicy evictionPolicy;

    /**
     * Creates a new configuration with default values.
     */
    public ServerConfig() {
        this.port = DEFAULT_PORT;
        this.maxMemory = DEFAULT_MAX_MEMORY;
        this.idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS;
        this.evictionPolicy = DEFAULT_EVICTION_POLICY;
    }

    // ===== Getters =====

    public int getPort() {
        return port;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    // ===== Setters =====

    public void setPort(int port) {
        this.port = port;
    }

    public void setMaxMemory(long maxMemory) {
        this.maxMemory = maxMemory;
    }

    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public void setEvictionPolicy(EvictionPolicy evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    /**
     * Parses command line arguments.
     *
     * @param args Command line arguments.
     */
    public void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (i + 1 >= args.length)
                continue;

            switch (args[i]) {
                case "--port":
                    this.port = Integer.parseInt(args[++i]);
                    break;
                case "--maxmemory":
                    this.maxMemory = parseMemorySize(args[++i]);
                    break;
                case "--timeout":
                    this.idleTimeoutMs = parseTimeMs(args[++i]);
                    break;
                case "--maxmemory-policy":
                    this.evictionPolicy = EvictionPolicy.parse(args[++i]);
                    break;
            }
        }
    }

    private long parseMemorySize(String value) {
        value = value.toLowerCase();
        long multiplier = 1;
        if (value.endsWith("m")) {
            multiplier = 1024 * 1024;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("g")) {
            multiplier = 1024L * 1024 * 1024;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("k")) {
            multiplier = 1024;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("mb")) { // Support mb
            multiplier = 1024 * 1024;
            value = value.substring(0, value.length() - 2);
        }
        return Long.parseLong(value) * multiplier;
    }

    private long parseTimeMs(String value) {
        value = value.toLowerCase();
        long multiplier = 1;
        if (value.endsWith("ms")) {
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("s")) {
            multiplier = 1000;
            value = value.substring(0, value.length() - 1);
        }
        return Long.parseLong(value) * multiplier;
    }
}
