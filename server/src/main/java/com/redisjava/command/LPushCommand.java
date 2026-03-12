package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisList;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.memory.EvictionManager;
import com.redisjava.network.Connection;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;

/**
 * LPUSH key value [value …]
 * <p>
 * Prepends one or more values to the head of a list.
 * If the key does not exist, a new list is created.
 * Returns the list length after the operation.
 * </p>
 */
public class LPushCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // args[0] = "LPUSH", args[1] = key, args[2..] = values
        if (args.length < 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        // Proactive eviction before write
        EvictionManager em = EvictionManager.getInstance();
        if (em != null && !em.ensureMemory()) {
            conn.write(Responses.OOM_ERROR);
            return;
        }

        Db db = Db.getInstance();
        byte[] key = args[1].getData();

        RedisObject obj = db.getObject(key);
        RedisList list;

        if (obj == null) {
            list = new RedisList();
            obj = RedisObject.list(list);
        } else if (!obj.isList()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        } else {
            list = obj.asList();
        }

        // Push all values left-to-right so the last value ends up at the head
        // (Redis LPUSH pushes each element one by one; LPUSH k a b c yields [c,b,a])
        for (int i = 2; i < args.length; i++) {
            list.lpush(args[i].getData());
        }

        // Persist the key (insert or update in place — no TTL for lists by default)
        db.set(key, obj);

        // AOF persistence: append the original command
        AofManager aof = AofManager.getInstance();
        if (aof != null) {
            aof.append(args);
        }

        conn.writeInteger(list.llen());
    }
}
