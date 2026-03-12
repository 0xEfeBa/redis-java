package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

public class DecrByCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // DECRBY key decrement
        if (args.length != 3) {
            conn.writeError("ERR wrong number of arguments for 'decrby' command");
            return;
        }

        long decrement;
        try {
            byte[] decrBytes = args[2].getBulkString();
            String s = new String(decrBytes);
            decrement = Long.parseLong(s);
        } catch (NumberFormatException e) {
            conn.writeError("ERR value is not an integer or out of range");
            return;
        }

        IncrCommand.executeIncrBy(conn, args, -decrement);
    }
}
