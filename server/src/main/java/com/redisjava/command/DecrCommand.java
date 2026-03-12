package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

public class DecrCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // DECR key
        if (args.length != 2) {
            conn.writeError("ERR wrong number of arguments for 'decr' command");
            return;
        }

        IncrCommand.executeIncrBy(conn, args, -1);
    }
}
