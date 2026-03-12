package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisList;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * LRANGE key start stop
 * <p>
 * Returns the elements of the list stored at key in the range [start, stop].
 * Both start and stop are zero-based indices.
 * Negative indices count from the end of the list (-1 = last element).
 * </p>
 */
public class LRangeCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 4) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        long start, stop;
        try {
            start = Long.parseLong(new String(args[2].getData()));
            stop  = Long.parseLong(new String(args[3].getData()));
        } catch (NumberFormatException e) {
            conn.write(Responses.ERR_NOT_INTEGER);
            return;
        }

        Db db = Db.getInstance();
        byte[] key = args[1].getData();
        RedisObject obj = db.getObject(key);

        if (obj == null) {
            // Empty list — return empty array
            conn.writeArrayStart(0);
            return;
        }
        if (!obj.isList()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        RedisList list = obj.asList();
        byte[][] elements = list.lrange(start, stop);

        conn.writeArrayStart(elements.length);
        for (byte[] elem : elements) {
            conn.writeBulkString(elem);
        }
    }
}
