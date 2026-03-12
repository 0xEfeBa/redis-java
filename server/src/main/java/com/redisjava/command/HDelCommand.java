package com.redisjava.command;

import com.redisjava.datastruct.*;
import com.redisjava.memory.MemoryManager;
import com.redisjava.network.Connection;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;

public class HDelCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 3) {
            conn.writeError("ERR wrong number of arguments for 'hdel' command");
            return;
        }

        Db db = Db.getInstance();
        MemoryManager memoryManager = db.getMemoryManager();
        AofManager aofManager = AofManager.getInstance();

        byte[] keyBytes = args[1].getBulkString();
        RString key = RString.fromBytes(keyBytes, memoryManager);
        RedisObject obj = db.get(key);

        if (obj == null || !obj.isHash()) {
            conn.writeInteger(0);
            return;
        }

        Dict hash = obj.asHash();
        int deleted = 0;

        for (int i = 2; i < args.length; i++) {
            byte[] fieldBytes = args[i].getBulkString();
            if (hash.remove(fieldBytes)) {
                deleted++;
            }
        }

        if (deleted > 0 && aofManager != null && aofManager.isEnabled()) {
            aofManager.append(args);
        }

        conn.writeInteger(deleted);
    }
}
