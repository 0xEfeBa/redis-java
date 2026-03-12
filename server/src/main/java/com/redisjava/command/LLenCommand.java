package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * LLEN key
 * <p>
 * Returns the length of the list stored at key.
 * Returns 0 when key does not exist.
 * </p>
 */
public class LLenCommand implements Command {

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
            conn.writeInteger(0);
            return;
        }
        if (!obj.isList()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        conn.writeInteger(obj.asList().llen());
    }
}
