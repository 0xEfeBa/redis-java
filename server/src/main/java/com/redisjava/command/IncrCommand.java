package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RString;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.memory.MemoryManager;
import com.redisjava.network.Connection;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;

public class IncrCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // INCR key
        if (args.length != 2) {
            conn.writeError("ERR wrong number of arguments for 'incr' command");
            return;
        }

        executeIncrBy(conn, args, 1);
    }

    // Shared logic can be moved to a helper or base class, but static helper here
    // is fine for now
    public static void executeIncrBy(Connection conn, RespToken[] args, long increment) {
        Db db = Db.getInstance();
        MemoryManager memoryManager = db.getMemoryManager();
        AofManager aofManager = AofManager.getInstance();

        byte[] keyBytes = args[1].getBulkString();
        RString key = RString.fromBytes(keyBytes, memoryManager);
        RedisObject obj = db.get(key);

        long value = 0;

        if (obj != null) {
            if (!obj.isString()) {
                conn.writeError("WRONGTYPE Operation against a key holding the wrong kind of value");
                return;
            }
            RString strVal = (RString) obj.getValue();
            try {
                // Parse integer from string bytes
                // RString stores bytes, need to convert to String then parseLong?
                // Or parse bytes directly. For simplicity now: String.
                String s = new String(strVal.getBytes()); // Optimized parsing later
                value = Long.parseLong(s);
            } catch (NumberFormatException e) {
                conn.writeError("ERR value is not an integer or out of range");
                return;
            }
        }

        value += increment;

        // Update DB
        String newValueStr = String.valueOf(value);
        RString newRStr = RString.fromBytes(newValueStr.getBytes(), memoryManager);
        db.set(key, RedisObject.string(newRStr));

        // AOF
        if (aofManager != null && aofManager.isEnabled()) {
            aofManager.append(args);
        }

        conn.writeInteger(value);
    }
}
