package com.redisjava.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServerConfig — argüman ayrıştırma ve varsayılan değer testleri.
 */
public class ServerConfigTest {

    // ── Varsayılan değerler ───────────────────────────────────────────────
    @Test

    public void testDefaults() {
        ServerConfig cfg = new ServerConfig();
        assertEquals(ServerConfig.DEFAULT_PORT, cfg.getPort());
        assertEquals(ServerConfig.DEFAULT_MAX_MEMORY, cfg.getMaxMemory());
        assertEquals(ServerConfig.DEFAULT_IDLE_TIMEOUT_MS, cfg.getIdleTimeoutMs());
    }

    // ── --maxmemory ayrıştırma ────────────────────────────────────────────
    @Test

    public void testParseMaxMemory_gigabyte() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "1g"});
        assertEquals(1024L * 1024 * 1024, cfg.getMaxMemory());
    }
    @Test

    public void testParseMaxMemory_megabytes() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "512mb"});
        assertEquals(512L * 1024 * 1024, cfg.getMaxMemory());
    }
    @Test

    public void testParseMaxMemory_megabyte_short() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "64m"});
        assertEquals(64L * 1024 * 1024, cfg.getMaxMemory());
    }
    @Test

    public void testParseMaxMemory_kilobytes() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "100k"});
        assertEquals(100L * 1024, cfg.getMaxMemory());
    }

    // ── --timeout ayrıştırma ──────────────────────────────────────────────
    @Test

    public void testParseTimeout_seconds() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--timeout", "10s"});
        assertEquals(10_000L, cfg.getIdleTimeoutMs());
    }
    @Test

    public void testParseTimeout_milliseconds() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--timeout", "500ms"});
        assertEquals(500L, cfg.getIdleTimeoutMs());
    }

    // ── Karma argümanlar ─────────────────────────────────────────────────
    @Test

    public void testParseMixedArgs() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--port", "9090", "--maxmemory", "2g", "--timeout", "30s"});
        assertEquals(9090, cfg.getPort());
        assertEquals(2L * 1024 * 1024 * 1024, cfg.getMaxMemory());
        assertEquals(30_000L, cfg.getIdleTimeoutMs());
    }

    // ── --port ayrıştırma ─────────────────────────────────────────────────
    @Test

    public void testParsePort() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--port", "6380"});
        assertEquals(6380, cfg.getPort());
    }
}
