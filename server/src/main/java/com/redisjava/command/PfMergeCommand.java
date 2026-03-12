package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.HyperLogLog;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * PFMERGE destkey sourcekey [sourcekey ...]
 *
 * <p>Merges one or more source HyperLogLogs into {@code destkey}.
 * If {@code destkey} already exists and is an HLL, the merge is
 * additive (union).  If it does not exist, a new HLL is created.
 *
 * <p>Returns {@code +OK}.
 */
public class PfMergeCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] destKey = args[1].getData();
        Db db = Db.getInstance();

        // Start with the existing destination (or empty)
        RedisObject destObj = db.getObject(destKey);
        HyperLogLog merged;

        if (destObj == null) {
            merged = new HyperLogLog();
        } else if (destObj.isHll()) {
            merged = destObj.asHll().copy();
        } else {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        // Merge all sources
        for (int i = 2; i < args.length; i++) {
            RedisObject src = db.getObject(args[i].getData());
            if (src == null) continue;
            if (!src.isHll()) {
                conn.write(Responses.WRONG_TYPE_ERROR);
                return;
            }
            merged.merge(src.asHll());
        }

        db.set(destKey, RedisObject.hll(merged));
        conn.write(Responses.OK);
    }
}
