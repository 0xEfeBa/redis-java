package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * ZSCORE key member
 * <p>
 * Returns the score of a member in the sorted set.
 * Returns a null bulk string when the key or member does not exist.
 * </p>
 */
public class ZScoreCommand implements Command {

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

        double score = obj.asZset().zscore(member);
        if (Double.isNaN(score)) {
            conn.writeNullBulkString();
        } else {
            String scoreStr = (score == Math.floor(score) && !Double.isInfinite(score))
                    ? String.valueOf((long) score)
                    : String.valueOf(score);
            conn.writeBulkString(scoreStr.getBytes());
        }
    }
}
