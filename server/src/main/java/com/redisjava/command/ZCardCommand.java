package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * ZCARD key
 * <p>
 * Returns the number of members in a sorted set.
 * Returns 0 when the key does not exist.
 * </p>
 */
public class ZCardCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 2) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        Db db = Db.getInstance();
        RedisObject obj = db.getObject(args[1].getData());

        if (obj == null) {
            conn.writeInteger(0);
            return;
        }
        if (!obj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        conn.writeInteger(obj.asZset().zcard());
    }
}
