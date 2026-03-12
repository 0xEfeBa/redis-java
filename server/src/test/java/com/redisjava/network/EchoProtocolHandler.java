package com.redisjava.network;

import java.nio.ByteBuffer;

/**
 * Echo protocol handler for testing purposes.
 * <p>
 * Simply echoes back all received data to the client.
 * </p>
 */
public class EchoProtocolHandler implements ProtocolHandler {

    @Override
    public void handle(Connection connection, ByteBuffer buffer) {
        // Read all available bytes
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        // Echo back to client
        connection.write(data);
    }

    @Override
    public void onConnect(Connection connection) {
        // Optional: could send welcome message
    }

    @Override
    public void onDisconnect(Connection connection) {
        // Cleanup if needed
    }
}
