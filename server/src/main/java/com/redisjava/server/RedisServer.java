package com.redisjava.server;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.EvictionManager;
import com.redisjava.memory.MemoryManager;
import com.redisjava.network.AeEventLoop;
import com.redisjava.persistence.AofLoader;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RedisProtocolHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Redis server main class.
 * <p>
 * Initializes all components and starts the event loop.
 * </p>
 * <p>
 * Usage: java -jar redis-java.jar [--port 6379]
 * </p>
 */
public class RedisServer {

    private static final String AOF_FILENAME = "appendonly.aof";

    private final ServerConfig config;
    private final AeEventLoop eventLoop;
    private final MemoryManager memoryManager;

    /**
     * Creates a new Redis server with the given configuration.
     *
     * @param config Server configuration.
     * @throws IOException If the server cannot bind to the port.
     */
    public RedisServer(ServerConfig config) throws IOException {
        this.config = config;

        // Calculate max chunks from max memory (CHUNK_SIZE = 16MB)
        // Calculate max chunks from max memory (CHUNK_SIZE = 16MB)
        int chunkSize = 16 * 1024 * 1024;
        int maxChunks = Math.max(1, (int) (config.getMaxMemory() / chunkSize));

        // Initialize memory manager with calculated limit
        this.memoryManager = new MemoryManager(maxChunks);

        // Initialize database with memory manager
        Db.init(memoryManager);

        // Initialize eviction manager with configured policy
        EvictionManager.init(memoryManager, config.getEvictionPolicy());

        // Initialize AOF persistence
        Path aofPath = Paths.get(AOF_FILENAME);
        AofManager.init(aofPath);

        // Load existing AOF data
        int loaded = AofLoader.load(aofPath, Db.getInstance());
        if (loaded > 0) {
            System.out.println("Loaded " + loaded + " commands from AOF");
        }

        // Open AOF for appending new commands
        AofManager.getInstance().open();

        // Create protocol handler
        RedisProtocolHandler handler = new RedisProtocolHandler();

        // Create and configure event loop with idle timeout from config
        this.eventLoop = new AeEventLoop(config.getPort(), handler, config.getIdleTimeoutMs());
    }

    /**
     * Starts the server.
     */
    public void start() {
        System.out.println("Redis-Java starting...");
        System.out.println("Port: " + config.getPort());
        System.out.println("Max Memory: " + (config.getMaxMemory() / 1024 / 1024) + "MB");
        System.out.println("AOF: enabled");
        System.out.println();
        System.out.println("Ready to accept connections");

        // Run the event loop (blocks until stopped)
        eventLoop.run();
    }

    /**
     * Stops the server.
     */
    public void stop() {
        eventLoop.stop();

        // Flush and close AOF
        AofManager aof = AofManager.getInstance();
        if (aof != null) {
            aof.sync();
            System.out.println("AOF Synced");
            aof.close();
        }

        // Shutdown DB and off-heap memory
        Db db = Db.getInstanceSafe();
        if (db != null) {
            db.shutdown();
        } else {
            memoryManager.shutdown();
        }
        System.out.println("Off-Heap Memory Cleared");
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        ServerConfig config = new ServerConfig();
        config.parseArgs(args);

        try {
            RedisServer server = new RedisServer(config);

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
