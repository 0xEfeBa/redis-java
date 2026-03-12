package com.redisjava.command;

import com.redisjava.datastruct.*;
import com.redisjava.memory.MemoryManager;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

public class HGetAllCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length != 2) {
            conn.writeError("ERR wrong number of arguments for 'hgetall' command");
            return;
        }

        Db db = Db.getInstance();
        MemoryManager memoryManager = db.getMemoryManager();

        byte[] keyBytes = args[1].getBulkString();
        RString key = RString.fromBytes(keyBytes, memoryManager);
        RedisObject obj = db.get(key);

        if (obj == null) {
            conn.writeArray(new RespToken[0]);
            return;
        }

        if (!obj.isHash()) {
            conn.writeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            return;
        }

        Dict hash = obj.asHash();

        // Array size: field + value pairs (2 * size)
        int size = hash.size();
        conn.writeArrayStart(size * 2);

        for (Dict.Entry entry : hash.entries()) {
            RString field = entry.getKey();
            RedisObject value = entry.getValue();

            if (value.isString()) {
                RString strValue = (RString) value.getValue();
                conn.writeBulkString(field.getBytes());
                conn.writeBulkString(strValue.getBytes());
            } else {
                conn.writeBulkString(field.getBytes());
                conn.writeError("ERR internal error: non-string in hash");
            }
        }
    }
}
