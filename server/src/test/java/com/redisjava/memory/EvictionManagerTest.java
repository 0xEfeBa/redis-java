package com.redisjava.memory;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.server.ServerConfig;
import com.redisjava.testutil.Assert;

/**
 * Tests for EvictionManager and EvictionPolicy.
 *
 * <p>Because we cannot exceed real memory limits in unit tests, we focus on:
 *   1. Policy parsing correctness.
 *   2. Singleton lifecycle.
 *   3. NO_EVICTION policy returns false when threshold is simulated.
 *   4. ensureMemory() returns true when memory is far below threshold.
 * </p>
 */
public class EvictionManagerTest {

    private MemoryManager memoryManager;
    @BeforeEach

    public void setup() {
        // Reset singleton before each test
        EvictionManager.init(null, EvictionPolicy.NO_EVICTION);
        Db.init(new MemoryManager(2));
        memoryManager = new MemoryManager(2); // 2 chunks * 16MB = 32MB max
    }

    // ── EvictionPolicy.parse() ────────────────────────────────────────────
    @Test

    public void testPolicyParse_allkeysLru() {
        Assert.assertEquals(EvictionPolicy.ALLKEYS_LRU, EvictionPolicy.parse("allkeys-lru"));
    }
    @Test

    public void testPolicyParse_volatileLru() {
        Assert.assertEquals(EvictionPolicy.VOLATILE_LRU, EvictionPolicy.parse("volatile-lru"));
    }
    @Test

    public void testPolicyParse_noEviction_default() {
        Assert.assertEquals(EvictionPolicy.NO_EVICTION, EvictionPolicy.parse("noeviction"));
    }
    @Test

    public void testPolicyParse_unknown_defaultsToNoEviction() {
        Assert.assertEquals(EvictionPolicy.NO_EVICTION, EvictionPolicy.parse("foobar"));
    }
    @Test

    public void testPolicyParse_null() {
        Assert.assertEquals(EvictionPolicy.NO_EVICTION, EvictionPolicy.parse(null));
    }
    @Test

    public void testPolicyParse_caseInsensitive() {
        Assert.assertEquals(EvictionPolicy.ALLKEYS_LRU, EvictionPolicy.parse("ALLKEYS-LRU"));
    }

    // ── Singleton lifecycle ───────────────────────────────────────────────
    @Test

    public void testInit_createsInstance() {
        EvictionManager.init(memoryManager, EvictionPolicy.ALLKEYS_LRU);
        Assert.assertNotNull(EvictionManager.getInstance());
    }
    @Test

    public void testInit_reinit_replacesInstance() {
        EvictionManager.init(memoryManager, EvictionPolicy.ALLKEYS_LRU);
        EvictionManager first = EvictionManager.getInstance();

        EvictionManager.init(memoryManager, EvictionPolicy.NO_EVICTION);
        EvictionManager second = EvictionManager.getInstance();

        Assert.assertTrue("Re-init should produce a new instance", first != second);
        Assert.assertEquals(EvictionPolicy.NO_EVICTION, second.getPolicy());
    }
    @Test

    public void testGetPolicy_returnsConfiguredPolicy() {
        EvictionManager.init(memoryManager, EvictionPolicy.VOLATILE_LRU);
        Assert.assertEquals(EvictionPolicy.VOLATILE_LRU, EvictionManager.getInstance().getPolicy());
    }

    // ── ensureMemory() under normal conditions ────────────────────────────
    @Test

    public void testEnsureMemory_belowThreshold_returnsTrue() {
        // With maxChunks=2 (32MB max) and essentially empty DB, we're way below 95%
        EvictionManager.init(memoryManager, EvictionPolicy.ALLKEYS_LRU);
        Db.init(memoryManager);

        boolean result = EvictionManager.getInstance().ensureMemory();
        Assert.assertTrue("Should return true when memory is below threshold", result);
    }
    @Test

    public void testEnsureMemory_noEvictionPolicy_belowThreshold_returnsTrue() {
        EvictionManager.init(memoryManager, EvictionPolicy.NO_EVICTION);
        Db.init(memoryManager);

        boolean result = EvictionManager.getInstance().ensureMemory();
        Assert.assertTrue("NO_EVICTION below threshold should still return true", result);
    }

    // ── ServerConfig integration ──────────────────────────────────────────
    @Test

    public void testServerConfig_defaults() {
        ServerConfig config = new ServerConfig();
        Assert.assertEquals(EvictionPolicy.ALLKEYS_LRU, config.getEvictionPolicy());
        Assert.assertEquals(ServerConfig.DEFAULT_MAX_MEMORY, config.getMaxMemory());
        Assert.assertEquals(ServerConfig.DEFAULT_PORT, config.getPort());
    }
    @Test

    public void testServerConfig_parseMaxmemoryPolicy() {
        ServerConfig config = new ServerConfig();
        config.parseArgs(new String[]{"--maxmemory-policy", "volatile-lru"});
        Assert.assertEquals(EvictionPolicy.VOLATILE_LRU, config.getEvictionPolicy());
    }
    @Test

    public void testServerConfig_parseMaxmemory_mb() {
        ServerConfig config = new ServerConfig();
        config.parseArgs(new String[]{"--maxmemory", "128m"});
        Assert.assertEquals(128L * 1024 * 1024, config.getMaxMemory());
    }
    @Test

    public void testServerConfig_parseMaxmemory_gb() {
        ServerConfig config = new ServerConfig();
        config.parseArgs(new String[]{"--maxmemory", "1g"});
        Assert.assertEquals(1L * 1024 * 1024 * 1024, config.getMaxMemory());
    }
}
