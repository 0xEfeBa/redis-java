package com.redisjava.network;

import java.nio.ByteBuffer;

/**
 * Interface for handling incoming network data.
 * <p>
 * This is the bridge between the Network Layer and the Protocol Layer.
 * The Network Layer is protocol-agnostic; it only reads/writes raw bytes.
 * Protocol-specific logic (e.g., Redis RESP) is implemented by classes
 * that implement this interface.
 * </p>
 * <p>
 * <b>Design Goal:</b> Keep the network package 100% pure and reusable.
 * It should know NOTHING about Redis, HTTP, or any specific protocol.
 * </p>
 */
public interface ProtocolHandler {

    /**
     * Called when data is available from a connection.
     * <p>
     * The buffer is in read mode (already flipped). The handler should
     * process as much data as possible and leave any incomplete data
     * in the buffer for the next call.
     * </p>
     *
     * @param connection The connection that received data.
     * @param buffer     The read buffer containing incoming data (read mode).
     */
    void handle(Connection connection, ByteBuffer buffer);

    /**
     * Called when a new connection is established.
     * <p>
     * Optional hook for connection setup (e.g., sending a welcome message).
     * Default implementation does nothing.
     * </p>
     *
     * @param connection The newly established connection.
     */
    default void onConnect(Connection connection) {
        // Default: no action
    }

    /**
     * Called when a connection is closed.
     * <p>
     * Optional hook for cleanup (e.g., removing from subscriber lists).
     * Default implementation does nothing.
     * </p>
     *
     * @param connection The connection being closed.
     */
    default void onDisconnect(Connection connection) {
        // Default: no action
    }

    /**
     * Periodic hook for background maintenance (e.g. TTL expiration).
     * <p>
     * Called by the event loop on a fixed interval.
     * Default implementation does nothing.
     * </p>
     *
     * @param now Cached current time from event loop.
     */
    default void onCron(long now) {
        // Default: no action
    }
}
