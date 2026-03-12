package com.redisjava.command;

import com.redisjava.datastruct.BloomFilter;
import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * BF.RESERVE key errorRate capacity
 *
 * <p>Creates a new Bloom filter at {@code key} with the specified
 * {@code errorRate} (a floating-point probability, e.g., {@code 0.01})
 * and {@code capacity} (expected number of items).
 *
 * <p>Returns {@code +OK} on success, or an error if the key already exists.
 */
public class BfReserveCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length != 4) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key = args[1].getData();

        double errorRate;
        int capacity;

        try {
            errorRate = Double.parseDouble(new String(args[2].getData()));
        } catch (NumberFormatException e) {
            conn.writeError("ERR error_rate is not a float");
            return;
        }

        try {
            capacity = Integer.parseInt(new String(args[3].getData()));
        } catch (NumberFormatException e) {
            conn.write(Responses.ERR_NOT_INTEGER);
            return;
        }

        if (errorRate <= 0 || errorRate >= 1) {
            conn.writeError("ERR error_rate must be between 0 and 1 exclusive");
            return;
        }
        if (capacity <= 0) {
            conn.writeError("ERR capacity must be > 0");
            return;
        }

        Db db = Db.getInstance();

        // If key exists (and is a bloom filter), return error like real Redis
        RedisObject existing = db.getObject(key);
        if (existing != null) {
            conn.writeError("ERR item exists");
            return;
        }

        BloomFilter bf = new BloomFilter(capacity, errorRate);
        db.set(key, RedisObject.bloom(bf));
        conn.write(Responses.OK);
    }
}
