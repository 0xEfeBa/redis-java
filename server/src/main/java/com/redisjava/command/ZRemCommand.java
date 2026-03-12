package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.datastruct.SkipList;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * ZREM key member [member …]
 * <p>
 * Removes one or more members from a sorted set.
 * Returns the number of members that were removed.
 * </p>
 */
public class ZRemCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        Db db = Db.getInstance();
        byte[] key = args[1].getData();
        RedisObject obj = db.getObject(key);

        if (obj == null) {
            conn.writeInteger(0);
            return;
        }
        if (!obj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        SkipList zset = obj.asZset();
        int removed = 0;
        for (int i = 2; i < args.length; i++) {
            if (zset.zrem(args[i].getData())) {
                removed++;
            }
        }

        // Remove key if sorted set is now empty
        if (zset.zcard() == 0) {
            db.del(key);
        }

        conn.writeInteger(removed);
    }
}
