package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.datastruct.SkipList;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.protocol.SafeEncoder;

/**
 * ZRANGE key start stop [WITHSCORES]
 * <p>
 * Returns elements from a sorted set in the range [start, stop]
 * (0-based, ascending by score). Supports negative indices and the
 * optional WITHSCORES flag that appends each score after its member.
 * </p>
 */
public class ZRangeCommand implements Command {

    private static final byte[] WITHSCORES = "WITHSCORES".getBytes();

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

        boolean withScores = args.length >= 5
                && SafeEncoder.equalsIgnoreCase(args[4].getData(), 0, args[4].getData().length, WITHSCORES);

        Db db = Db.getInstance();
        byte[] key = args[1].getData();
        RedisObject obj = db.getObject(key);

        if (obj == null) {
            conn.writeArrayStart(0);
            return;
        }
        if (!obj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        SkipList.ZEntry[] entries = obj.asZset().zrange(start, stop);

        int arrayLen = withScores ? entries.length * 2 : entries.length;
        conn.writeArrayStart(arrayLen);
        for (SkipList.ZEntry e : entries) {
            conn.writeBulkString(e.member);
            if (withScores) {
                // Score formatted like Redis: no trailing zeros when integer
                String scoreStr = (e.score == Math.floor(e.score) && !Double.isInfinite(e.score))
                        ? String.valueOf((long) e.score)
                        : String.valueOf(e.score);
                conn.writeBulkString(scoreStr.getBytes());
            }
        }
    }
}
