package com.redisjava.stats;

/**
 * Server statistics tracker.
 * <p>
 * Tracks connection and command counts for INFO command.
 * Thread-safe via volatile fields (single writer assumption).
 * </p>
 */
public class ServerStats {

    private static final ServerStats INSTANCE = new ServerStats();

    private volatile long totalConnections = 0;
    private volatile long totalCommands = 0;
    private volatile long currentConnections = 0;
    private volatile long keyspaceHits = 0;
    private volatile long keyspaceMisses = 0;
    private volatile long expiredKeys = 0;
    private volatile long evictedKeys = 0;

    private ServerStats() {
    }

    /**
     * Returns the singleton instance.
     */
    public static ServerStats getInstance() {
        return INSTANCE;
    }

    /**
     * Called when a new connection is accepted.
     */
    public void connectionOpened() {
        totalConnections++;
        currentConnections++;
    }

    /**
     * Called when a connection is closed.
     */
    public void connectionClosed() {
        if (currentConnections > 0) {
            currentConnections--;
        }
    }

    /**
     * Called when a command is executed.
     */
    public void commandExecuted() {
        totalCommands++;
    }

    public void keyHit() {
        keyspaceHits++;
    }

    public void keyMiss() {
        keyspaceMisses++;
    }

    public void keyExpired(long count) {
        expiredKeys += count;
    }

    public void keyEvicted(long count) {
        evictedKeys += count;
    }

    // ===== Getters =====

    public long getTotalConnections() {
        return totalConnections;
    }

    public long getTotalCommands() {
        return totalCommands;
    }

    public long getCurrentConnections() {
        return currentConnections;
    }

    public long getKeyspaceHits() {
        return keyspaceHits;
    }

    public long getKeyspaceMisses() {
        return keyspaceMisses;
    }

    public long getExpiredKeys() {
        return expiredKeys;
    }

    public long getEvictedKeys() {
        return evictedKeys;
    }
}
