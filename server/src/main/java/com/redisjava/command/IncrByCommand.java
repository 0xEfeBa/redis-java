package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

public class IncrByCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // INCRBY key increment
        if (args.length != 3) {
            conn.writeError("ERR wrong number of arguments for 'incrby' command");
            return;
        }

        long increment;
        try {
            byte[] incrBytes = args[2].getBulkString();
            String s = new String(incrBytes);
            increment = Long.parseLong(s);
        } catch (NumberFormatException e) {
            conn.writeError("ERR value is not an integer or out of range");
            return;
        }

        IncrCommand.executeIncrBy(conn, args, increment);
    }
}
