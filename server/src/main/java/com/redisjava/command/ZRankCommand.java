package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.datastruct.SkipList;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * ZRANK key member
 * <p>
 * Returns the rank (0-based index) of a member in the sorted set,
 * ordered by ascending score. Returns a null bulk string if the member
 * does not exist.
 * </p>
 */
public class ZRankCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        Db db = Db.getInstance();
        byte[] key    = args[1].getData();
        byte[] member = args[2].getData();

        RedisObject obj = db.getObject(key);
        if (obj == null) {
            conn.writeNullBulkString();
            return;
        }
        if (!obj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        long rank = obj.asZset().zrank(member);
        if (rank == -1) {
            conn.writeNullBulkString();
        } else {
            conn.writeInteger(rank);
        }
    }
}
