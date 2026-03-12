package com.redisjava.server;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;

/**
 * ServerConfig — argüman ayrıştırma ve varsayılan değer testleri.
 */
public class ServerConfigTest {

    // ── Varsayılan değerler ───────────────────────────────────────────────
    @Test

    public void testDefaults() {
        ServerConfig cfg = new ServerConfig();
        Assert.assertEquals(ServerConfig.DEFAULT_PORT, cfg.getPort());
        Assert.assertEquals(ServerConfig.DEFAULT_MAX_MEMORY, cfg.getMaxMemory());
        Assert.assertEquals(ServerConfig.DEFAULT_IDLE_TIMEOUT_MS, cfg.getIdleTimeoutMs());
    }

    // ── --maxmemory ayrıştırma ────────────────────────────────────────────
    @Test

    public void testParseMaxMemory_gigabyte() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "1g"});
        Assert.assertEquals(1024L * 1024 * 1024, cfg.getMaxMemory());
    }
    @Test

    public void testParseMaxMemory_megabytes() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "512mb"});
        Assert.assertEquals(512L * 1024 * 1024, cfg.getMaxMemory());
    }
    @Test

    public void testParseMaxMemory_megabyte_short() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "64m"});
        Assert.assertEquals(64L * 1024 * 1024, cfg.getMaxMemory());
    }
    @Test

    public void testParseMaxMemory_kilobytes() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--maxmemory", "100k"});
        Assert.assertEquals(100L * 1024, cfg.getMaxMemory());
    }

    // ── --timeout ayrıştırma ──────────────────────────────────────────────
    @Test

    public void testParseTimeout_seconds() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--timeout", "10s"});
        Assert.assertEquals(10_000L, cfg.getIdleTimeoutMs());
    }
    @Test

    public void testParseTimeout_milliseconds() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--timeout", "500ms"});
        Assert.assertEquals(500L, cfg.getIdleTimeoutMs());
    }

    // ── Karma argümanlar ─────────────────────────────────────────────────
    @Test

    public void testParseMixedArgs() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--port", "9090", "--maxmemory", "2g", "--timeout", "30s"});
        Assert.assertEquals(9090, cfg.getPort());
        Assert.assertEquals(2L * 1024 * 1024 * 1024, cfg.getMaxMemory());
        Assert.assertEquals(30_000L, cfg.getIdleTimeoutMs());
    }

    // ── --port ayrıştırma ─────────────────────────────────────────────────
    @Test

    public void testParsePort() {
        ServerConfig cfg = new ServerConfig();
        cfg.parseArgs(new String[]{"--port", "6380"});
        Assert.assertEquals(6380, cfg.getPort());
    }
}
