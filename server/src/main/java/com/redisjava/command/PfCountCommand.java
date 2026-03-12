package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.HyperLogLog;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * PFCOUNT key [key ...]
 *
 * <p>Returns the estimated cardinality of the HyperLogLog(s) at the given keys.
 * When multiple keys are given, returns the cardinality of the union of all HLLs.
 * Missing keys are treated as empty HLLs.
 */
public class PfCountCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 2) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        Db db = Db.getInstance();

        if (args.length == 2) {
            // Single key — fast path
            byte[] key = args[1].getData();
            RedisObject obj = db.getObject(key);
            if (obj == null) {
                conn.writeInteger(0);
                return;
            }
            if (!obj.isHll()) {
                conn.write(Responses.WRONG_TYPE_ERROR);
                return;
            }
            conn.writeInteger(obj.asHll().count());
        } else {
            // Multiple keys — merge into a temporary HLL
            HyperLogLog merged = new HyperLogLog();
            for (int i = 1; i < args.length; i++) {
                RedisObject obj = db.getObject(args[i].getData());
                if (obj == null) continue;
                if (!obj.isHll()) {
                    conn.write(Responses.WRONG_TYPE_ERROR);
                    return;
                }
                merged.merge(obj.asHll());
            }
            conn.writeInteger(merged.count());
        }
    }
}
