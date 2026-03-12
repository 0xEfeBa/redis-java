package com.redisjava.command;

import com.redisjava.datastruct.BloomFilter;
import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * BF.EXISTS key member
 *
 * <p>Returns:
 * <ul>
 *   <li>{@code :1} if the member might be in the set (with configured probability).</li>
 *   <li>{@code :0} if the member is definitely not in the set.</li>
 * </ul>
 */
public class BfExistsCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length != 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key    = args[1].getData();
        byte[] member = args[2].getData();
        Db db = Db.getInstance();

        RedisObject obj = db.getObject(key);
        if (obj == null) {
            conn.writeInteger(0);
            return;
        }
        if (!obj.isBloom()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        conn.writeInteger(obj.asBloom().mightContain(member) ? 1 : 0);
    }
}
