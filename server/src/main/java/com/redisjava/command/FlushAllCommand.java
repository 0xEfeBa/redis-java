package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * FLUSHALL command implementation.
 * Clears all data from the database.
 */
public class FlushAllCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        Db.getInstance().flushAll();
        connection.write(Responses.OK);
    }
}
