package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.EvictionManager;
import com.redisjava.network.Connection;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;
import com.redisjava.protocol.SafeEncoder;
import com.redisjava.stats.ServerStats;

/**
 * SET command implementation.
 * <p>
 * Supports: SET key value [NX|XX] [EX seconds|PX milliseconds]
 * Also handles SETNX alias (zero-allocation, case-insensitive match).
 * </p>
 */
public class SetCommand implements Command {

    // Legacy constants kept for fallback path (when EvictionManager is not initialized)
    private static final int EVICTION_LRU_SAMPLES = 20;
    private static final int EVICTION_MAX_TRIES = 32;
    private static final int EXPIRE_SAMPLES_ON_OOM = 32;

    // Pre-allocated byte arrays for zero-allocation option parsing
    private static final byte[] CMD_SETNX = "SETNX".getBytes();
    private static final byte[] OPT_NX    = "NX".getBytes();
    private static final byte[] OPT_XX    = "XX".getBytes();
    private static final byte[] OPT_EX    = "EX".getBytes();
    private static final byte[] OPT_PX    = "PX".getBytes();

    @Override
    public void execute(Connection connection, RespToken[] args) {
        // SET key value [NX|XX] [EX seconds|PX milliseconds]
        if (args.length < 3) {
            connection.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key = args[1].getData();
        byte[] value = args[2].getData();

        if (key == null || value == null) {
            connection.write(Responses.ERR_SYNTAX);
            return;
        }

        boolean nx = false;
        boolean xx = false;
        long expireAtMs = 0;
        long now = System.currentTimeMillis();

        // Zero-allocation SETNX alias detection
        byte[] cmdBytes = args[0].getData();
        boolean isSetNxCommand = SafeEncoder.equalsIgnoreCase(
                cmdBytes, 0, cmdBytes.length, CMD_SETNX);
        if (isSetNxCommand) {
            nx = true;
        }

        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                byte[] optBytes = args[i].getData();
                if (SafeEncoder.equalsIgnoreCase(optBytes, 0, optBytes.length, OPT_NX)) {
                    nx = true;
                } else if (SafeEncoder.equalsIgnoreCase(optBytes, 0, optBytes.length, OPT_XX)) {
                    xx = true;
                } else if (SafeEncoder.equalsIgnoreCase(optBytes, 0, optBytes.length, OPT_EX)) {
                    if (i + 1 >= args.length) {
                        connection.write(Responses.ERR_SYNTAX);
                        return;
                    }
                    long seconds = Long.parseLong(new String(args[++i].getData()));
                    if (seconds <= 0) {
                        connection.write(Responses.ERR_SYNTAX);
                        return;
                    }
                    expireAtMs = now + (seconds * 1000L);
                } else if (SafeEncoder.equalsIgnoreCase(optBytes, 0, optBytes.length, OPT_PX)) {
                    if (i + 1 >= args.length) {
                        connection.write(Responses.ERR_SYNTAX);
                        return;
                    }
                    long ms = Long.parseLong(new String(args[++i].getData()));
                    if (ms <= 0) {
                        connection.write(Responses.ERR_SYNTAX);
                        return;
                    }
                    expireAtMs = now + ms;
                } else {
                    connection.write(Responses.ERR_SYNTAX);
                    return;
                }
            }
        }

        Db db = Db.getInstance();
        boolean exists = (db.getObject(key) != null);

        if (nx && exists) {
            if (isSetNxCommand) {
                connection.writeInteger(0);
            } else {
                connection.write(Responses.NULL_BULK_STRING);
            }
            return;
        }
        if (xx && !exists) {
            connection.write(Responses.NULL_BULK_STRING);
            return;
        }

        // Proactive eviction: check memory before allocating.
        // EvictionManager handles policy-based eviction (ALLKEYS_LRU etc.).
        // Falls back to inline logic if EvictionManager is not initialized (tests).
        EvictionManager evictionManager = EvictionManager.getInstance();
        if (evictionManager != null) {
            if (!evictionManager.ensureMemory()) {
                connection.write(Responses.ERR_OOM);
                return;
            }
        }

        boolean setOk = false;
        try {
            db.set(key, value, expireAtMs);
            setOk = true;
        } catch (RuntimeException oom) {
            // Fallback inline eviction (used when EvictionManager is not initialized)
            long nowEvict = System.currentTimeMillis();

            int expired = db.activeExpireCycle(EXPIRE_SAMPLES_ON_OOM, nowEvict);
            if (expired > 0) {
                ServerStats.getInstance().keyExpired(expired);
            }

            int evicted = 0;
            for (int i = 0; i < EVICTION_MAX_TRIES; i++) {
                boolean didEvict = db.evictOneLru(EVICTION_LRU_SAMPLES, nowEvict);
                if (!didEvict) break;
                evicted++;
                try {
                    db.set(key, value, expireAtMs);
                    setOk = true;
                    break;
                } catch (RuntimeException ignore) {
                    // keep evicting
                }
            }
            if (evicted > 0) {
                ServerStats.getInstance().keyEvicted(evicted);
            }
        }

        if (!setOk) {
            connection.write(Responses.ERR_OOM);
            return;
        }

        // Previous expireAt call is now redundant as it's handled in set()
        // If expireAtMs was 0, it means no expiry (persist), which is also handled by
        // Dict.put

        // Append to AOF if enabled
        AofManager aof = AofManager.getInstance();
        if (aof != null && aof.isEnabled()) {
            aof.append(args);
        }

        if (isSetNxCommand) {
            connection.writeInteger(1);
        } else {
            connection.write(Responses.OK);
        }
    }
}
