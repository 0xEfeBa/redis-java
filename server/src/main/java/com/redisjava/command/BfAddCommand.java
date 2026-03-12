package com.redisjava.command;

import com.redisjava.datastruct.BloomFilter;
import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * BF.ADD key member
 *
 * <p>Adds {@code member} to the Bloom filter stored at {@code key}.
 * If the key does not exist, a new filter is created with the default
 * capacity (1 000 items) and error rate (1%).
 *
 * <p>Returns:
 * <ul>
 *   <li>{@code :1} if this is the first time the member was added.</li>
 *   <li>{@code :0} if the member was already (probably) present.</li>
 * </ul>
 *
 * <p>Note: Bloom filters have false positives but no false negatives.
 * A return of {@code :0} does <em>not</em> guarantee the item was
 * previously added; it may be a false positive.
 */
public class BfAddCommand implements Command {

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
        BloomFilter bf;

        if (obj == null) {
            // Create with defaults
            bf = new BloomFilter(1000, 0.01);
            db.set(key, RedisObject.bloom(bf));
        } else if (obj.isBloom()) {
            bf = obj.asBloom();
        } else {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        boolean wasAbsent = !bf.mightContain(member);
        bf.add(member);
        conn.writeInteger(wasAbsent ? 1 : 0);
    }
}
