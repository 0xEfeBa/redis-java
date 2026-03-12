package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * TTL command implementation.
 *
 * TTL key
 * Returns:
 * -2 if the key does not exist
 * -1 if the key exists but has no associated expire
 * >= 0 TTL in seconds
 */
public class TtlCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        if (args.length != 2) {
            connection.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key = args[1].getBulkString();
        if (key == null) {
            connection.write(Responses.ERR_SYNTAX);
            return;
        }

        long ttlSeconds = Db.getInstance().ttlSeconds(key);
        connection.writeInteger(ttlSeconds);
    }
}

