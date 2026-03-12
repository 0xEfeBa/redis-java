package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.memory.DirectBufferUtils;

public class MockConnection extends Connection {
    private final StringBuilder buffer = new StringBuilder();
    private boolean mockClosed = false;

    public MockConnection() {
        super(null, null); // SocketChannel and SelectionKey can be null for testing
    }

    /** Marks this connection as closed without touching NIO resources. */
    @Override
    public void close() {
        mockClosed = true;
    }

    @Override
    public boolean isClosed() {
        return mockClosed;
    }

    @Override
    public void write(byte[] data) {
        buffer.append(new String(data));
    }

    @Override
    public void writeFromAddress(long address, int length) {
        if (length <= 0) {
            return;
        }
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = DirectBufferUtils.getByte(address + i);
        }
        buffer.append(new String(data));
    }

    public String getLastResponse() {
        if (buffer.length() == 0) {
            return null;
        }
        return buffer.toString();
    }

    public void clear() {
        buffer.setLength(0);
    }
}
