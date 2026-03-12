package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisList;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * RPOP key
 * <p>
 * Removes and returns the last element of the list stored at key.
 * Returns a bulk string, or null bulk string when key doesn't exist / list is empty.
 * </p>
 */
public class RPopCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 2) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        Db db = Db.getInstance();
        byte[] key = args[1].getData();
        RedisObject obj = db.getObject(key);

        if (obj == null) {
            conn.writeNullBulkString();
            return;
        }
        if (!obj.isList()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        RedisList list = obj.asList();
        byte[] val = list.rpop();

        if (val == null) {
            conn.writeNullBulkString();
        } else {
            conn.writeBulkString(val);
            if (list.isEmpty()) {
                db.del(key);
            }
        }
    }
}
