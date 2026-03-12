package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.network.Connection;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;

/**
 * PERSIST command implementation.
 *
 * PERSIST key
 * Returns: 1 if timeout was removed, 0 otherwise.
 */
public class PersistCommand implements Command {

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

        boolean removed = Db.getInstance().persistIfHasExpire(key);
        if (removed) {
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

