package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;
import com.redisjava.network.Connection;

/**
 * EXPIRE command implementation.
 *
 * EXPIRE key seconds
 * Returns: 1 if the timeout was set, 0 if key does not exist.
 *
 * Redis-like semantics: seconds <= 0 means delete the key immediately.
 */
public class ExpireCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        if (args.length != 3) {
            connection.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key = args[1].getBulkString();
        byte[] secondsBytes = args[2].getBulkString();
        if (key == null || secondsBytes == null) {
            connection.write(Responses.ERR_SYNTAX);
            return;
        }

        final long seconds;
        try {
            seconds = Long.parseLong(new String(secondsBytes));
        } catch (NumberFormatException e) {
            connection.write(Responses.ERR_SYNTAX);
            return;
        }

        Db db = Db.getInstance();

        // Redis behavior: <= 0 seconds => delete key immediately if it exists.
        if (seconds <= 0) {
            boolean deleted = db.del(key);
            if (deleted) {
                AofManager aof = AofManager.getInstance();
                if (aof != null && aof.isEnabled()) {
                    aof.append(args);
                }
                connection.writeInteger(1);
            } else {
                connection.writeInteger(0);
            }
            return;
        }

        long expireAtMs = System.currentTimeMillis() + (seconds * 1000L);
        boolean set = db.expireAt(key, expireAtMs);

        if (set) {
            AofManager aof = AofManager.getInstance();
            if (aof != null && aof.isEnabled()) {
                aof.append(args);
            }
            connection.writeInteger(1);
        } else {
            connection.writeInteger(0);
        }
    }
}

