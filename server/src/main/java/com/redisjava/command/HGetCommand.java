package com.redisjava.command;

import com.redisjava.datastruct.*;
import com.redisjava.memory.MemoryManager;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

public class HGetCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length != 3) {
            conn.writeError("ERR wrong number of arguments for 'hget' command");
            return;
        }

        Db db = Db.getInstance();
        MemoryManager memoryManager = db.getMemoryManager();

        byte[] keyBytes = args[1].getBulkString();
        byte[] fieldBytes = args[2].getBulkString();

        RString key = RString.fromBytes(keyBytes, memoryManager);
        RedisObject obj = db.get(key);

        if (obj == null) {
            conn.writeNullBulkString();
            return;
        }

        if (!obj.isHash()) {
            conn.writeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            return;
        }

        Dict hash = obj.asHash();
        RedisObject value = hash.get(fieldBytes);

        if (value == null) {
            conn.writeNullBulkString();
        } else {
            RString strValue = (RString) value.getValue();
            conn.writeBulkString(strValue.getBytes());
        }
    }
}
