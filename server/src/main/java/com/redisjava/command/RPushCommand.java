package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisList;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.memory.EvictionManager;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * RPUSH key value [value …]
 * <p>
 * Appends one or more values to the tail of a list.
 * If the key does not exist, a new list is created.
 * Returns the list length after the operation.
 * </p>
 */
public class RPushCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

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

        for (int i = 2; i < args.length; i++) {
            list.rpush(args[i].getData());
        }

        db.set(key, obj);

        conn.writeInteger(list.llen());
    }
}
