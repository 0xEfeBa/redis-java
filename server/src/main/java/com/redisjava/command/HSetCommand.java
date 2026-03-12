package com.redisjava.command;

import com.redisjava.datastruct.*;
import com.redisjava.memory.MemoryManager;
import com.redisjava.network.Connection;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;

public class HSetCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // HSET key field value [field value ...]
        // args[0]=HSET, args[1]=key, then pairs of field+value
        if (args.length < 4 || (args.length - 2) % 2 != 0) {
            conn.writeError("ERR wrong number of arguments for 'hset' command");
            return;
        }

        Db db = Db.getInstance();
        MemoryManager memoryManager = db.getMemoryManager();
        AofManager aofManager = AofManager.getInstance();

        byte[] keyBytes = args[1].getBulkString();
        RString key = RString.fromBytes(keyBytes, memoryManager);
        RedisObject obj = db.get(key);

        if (obj == null) {
            Dict hash = new Dict(memoryManager);
            obj = RedisObject.hash(hash);
            db.set(key, obj);
        } else if (!obj.isHash()) {
            conn.writeError("WRONGTYPE Operation against a key holding the wrong kind of value");
            return;
        }

        Dict hash = obj.asHash();
        int newFields = 0;

        for (int i = 2; i < args.length; i += 2) {
            byte[] fieldBytes = args[i].getBulkString();
            byte[] valueBytes = args[i + 1].getBulkString();

            RedisObject existing = hash.get(fieldBytes);
            if (existing == null) newFields++;

            RString valueStr = RString.fromBytes(valueBytes, memoryManager);
            hash.put(fieldBytes, RedisObject.string(valueStr));
        }

        if (aofManager != null && aofManager.isEnabled()) {
            aofManager.append(args);
        }

        conn.writeInteger(newFields);
    }
}
