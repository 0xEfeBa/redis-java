package com.redisjava.command;

import com.redisjava.datastruct.*;
import com.redisjava.memory.MemoryManager;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

public class HExistsCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length != 3) {
            conn.writeError("ERR wrong number of arguments for 'hexists' command");
            return;
        }

        Db db = Db.getInstance();
        MemoryManager memoryManager = db.getMemoryManager();

        byte[] keyBytes = args[1].getBulkString();
        RString key = RString.fromBytes(keyBytes, memoryManager);
        RedisObject obj = db.get(key);

        if (obj == null || !obj.isHash()) {
            conn.writeInteger(0);
            return;
        }

        Dict hash = obj.asHash();
        byte[] fieldBytes = args[2].getBulkString();
        RedisObject result = hash.get(fieldBytes);

        conn.writeInteger(result != null ? 1 : 0);
    }
}
