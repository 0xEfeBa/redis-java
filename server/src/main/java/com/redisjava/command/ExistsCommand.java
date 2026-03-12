package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.stats.ServerStats;

public class ExistsCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // EXISTS key [key ...]
        if (args.length < 2) {
            conn.writeError("ERR wrong number of arguments for 'exists' command");
            return;
        }

        Db db = Db.getInstance();
        int count = 0;

        for (int i = 1; i < args.length; i++) {
            byte[] keyBytes = args[i].getBulkString();
            if (db.getObject(keyBytes) != null) {
                count++;
                ServerStats.getInstance().keyHit();
            } else {
                ServerStats.getInstance().keyMiss();
            }
        }

        conn.writeInteger(count);
    }
}
