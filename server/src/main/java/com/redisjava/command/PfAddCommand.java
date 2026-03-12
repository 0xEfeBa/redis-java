package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.HyperLogLog;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * PFADD key element [element ...]
 *
 * <p>Adds one or more elements to the HyperLogLog at {@code key}.
 * Creates the HLL if the key does not exist.
 *
 * <p>Returns:
 * <ul>
 *   <li>{@code :1} if the approximated cardinality changed.</li>
 *   <li>{@code :0} if it did not change.</li>
 * </ul>
 */
public class PfAddCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key = args[1].getData();
        Db db = Db.getInstance();

        RedisObject obj = db.getObject(key);
        HyperLogLog hll;

        if (obj == null) {
            hll = new HyperLogLog();
            db.set(key, RedisObject.hll(hll));
        } else if (obj.isHll()) {
            hll = obj.asHll();
        } else {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        boolean changed = false;
        for (int i = 2; i < args.length; i++) {
            if (hll.add(args[i].getData())) {
                changed = true;
            }
        }

        conn.writeInteger(changed ? 1 : 0);
    }
}
