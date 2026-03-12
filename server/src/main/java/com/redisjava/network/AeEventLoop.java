package com.redisjava.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

/**
 * Non-blocking event loop for handling network I/O.
 * <p>
 * Inspired by Redis's ae.c (A simple event-driven programming library).
 * Uses Java NIO {@link Selector} to handle thousands of connections
 * with a single thread.
 * </p>
 * <p>
 * <b>WARNING:</b> This class is NOT thread-safe. It is designed to be
 * used within a single-threaded context. Do not call methods from
 * multiple threads.
 * </p>
 */
public class AeEventLoop {

    private static final int SELECT_TIMEOUT_MS = 1000; // 1 second for idle check
    private static final long IDLE_CHECK_INTERVAL_MS = 1000; // Check idle every 1 second

    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final ProtocolHandler handler;
    private final int port;
    private final long idleTimeoutMs;

    private volatile boolean running = false;
    private long lastIdleCheckTime = 0;

    /**
     * Creates a new event loop with default idle timeout.
     *
     * @param port    The port to listen on. Use 0 for random available port.
     * @param handler The protocol handler for processing incoming data.
     * @throws IOException If the server socket cannot be opened.
     */
    public AeEventLoop(int port, ProtocolHandler handler) throws IOException {
        this(port, handler, Connection.DEFAULT_IDLE_TIMEOUT_MS);
    }

    /**
     * Creates a new event loop with custom idle timeout.
     *
     * @param port          The port to listen on. Use 0 for random available port.
     * @param handler       The protocol handler for processing incoming data.
     * @param idleTimeoutMs Idle timeout for connections in milliseconds.
     * @throws IOException If the server socket cannot be opened.
     */
    public AeEventLoop(int port, ProtocolHandler handler, long idleTimeoutMs) throws IOException {
        this.handler = handler;
        this.idleTimeoutMs = idleTimeoutMs;
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();

        // Configure server channel
        serverChannel.configureBlocking(false);
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Get actual port (important if port was 0)
        this.port = serverChannel.socket().getLocalPort();
    }

    /**
     * Starts the event loop. Blocks until {@link #stop()} is called.
     */
    public void run() {
        running = true;

        while (running) {
            try {
                processEvents();
            } catch (IOException e) {
                // Log but continue - one bad event shouldn't kill the server
                System.err.println("Error in event loop: " + e.getMessage());
            }
        }

        cleanup();
    }

    /**
     * Processes one round of I/O events.
     */
    private void processEvents() throws IOException {
        int readyCount = selector.select(SELECT_TIMEOUT_MS);

        // Get cached time once per loop
        long now = System.currentTimeMillis();

        if (readyCount > 0) {
            Set<SelectionKey> selectedKeys = selector.selectedKeys();

            // Optimized: iterate without creating Iterator object
            for (SelectionKey key : selectedKeys) {
                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    handleAccept(now);
                }

                if (key.isReadable()) {
                    handleRead(key, now);
                }

                if (key.isValid() && key.isWritable()) {
                    handleWrite(key);
                }

                Connection connection = (Connection) key.attachment();
                if (connection != null && connection.isCloseRequested()) {
                    closeConnection(connection);
                }
            }
            selectedKeys.clear(); // Clear all at once instead of per-iteration
        }

        // Check for idle connections periodically (not every loop)
        if (now - lastIdleCheckTime > IDLE_CHECK_INTERVAL_MS) {
            // Background maintenance hook (e.g. TTL expiration)
            handler.onCron(now);
            checkIdleConnections(now);
            lastIdleCheckTime = now;
        }
    }

    /**
     * Handles new connection acceptance (OP_ACCEPT).
     */
    private void handleAccept(long now) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel == null) {
            return; // No pending connection (shouldn't happen, but be safe)
        }

        // Configure client channel
        clientChannel.configureBlocking(false);
        clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);

        // Register for read events
        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

        // Create connection and attach to key
        Connection connection = new Connection(clientChannel, clientKey, idleTimeoutMs);
        connection.updateActivity(now); // Set initial activity with cached time
        clientKey.attach(connection);

        // Notify handler
        handler.onConnect(connection);
    }

    /**
     * Handles incoming data (OP_READ).
     */
    private void handleRead(SelectionKey key, long now) {
        Connection connection = (Connection) key.attachment();
        if (connection == null || connection.isClosed()) {
            return;
        }

        try {
            int bytesRead = connection.read();

            if (bytesRead == -1) {
                // Connection closed by client
                closeConnection(connection);
                return;
            }

            if (bytesRead > 0) {
                // Update activity with cached time (no syscall!)
                connection.updateActivity(now);

                // Flip buffer for reading and pass to handler
                connection.getReadBuffer().flip();
                handler.handle(connection, connection.getReadBuffer());

                // Compact buffer for next read (preserves unprocessed data)
                connection.getReadBuffer().compact();
            }
        } catch (IOException e) {
            // Connection error - close it
            closeConnection(connection);
        }
    }

    /**
     * Handles outgoing data (OP_WRITE).
     */
    private void handleWrite(SelectionKey key) {
        Connection connection = (Connection) key.attachment();
        if (connection == null || connection.isClosed()) {
            return;
        }

        try {
            connection.flush();
        } catch (IOException e) {
            closeConnection(connection);
        }
    }

    /**
     * Checks for and closes idle connections.
     */
    private void checkIdleConnections(long now) {
        for (SelectionKey key : selector.keys()) {
            if (key.attachment() instanceof Connection) {
                Connection connection = (Connection) key.attachment();
                if (connection.isIdleTimeout(now)) {
                    closeConnection(connection);
                }
            }
        }
    }

    /**
     * Closes a connection and notifies the handler.
     */
    private void closeConnection(Connection connection) {
        handler.onDisconnect(connection);
        connection.close();
    }

    /**
     * Signals the event loop to stop.
     */
    public void stop() {
        running = false;
        selector.wakeup(); // Interrupt select() if blocked
    }

    /**
     * Cleans up resources when the event loop ends.
     */
    private void cleanup() {
        try {
            // Close all client connections
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() instanceof Connection) {
                    ((Connection) key.attachment()).close();
                }
            }
            serverChannel.close();
            selector.close();
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    /**
     * @return The port this server is listening on.
     */
    public int getPort() {
        return port;
    }

    /**
     * @return true if the event loop is running.
     */
    public boolean isRunning() {
        return running;
    }
}
