package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.network.Connection;
import com.redisjava.persistence.AofManager;
import com.redisjava.protocol.RespToken;

/**
 * DEL command implementation.
 * <p>
 * DEL key [key ...] - removes one or more keys.
 * </p>
 */
public class DelCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        // DEL key [key ...] (minimum 2 args: command + at least one key)
        if (args.length < 2) {
            connection.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        int deleted = 0;
        Db db = Db.getInstance();
        AofManager aof = AofManager.getInstance();
        boolean aofEnabled = aof != null && aof.isEnabled();

        for (int i = 1; i < args.length; i++) {
            byte[] key = args[i].getData();
            if (key != null && db.del(key)) {
                deleted++;
            }
        }

        // Append to AOF if enabled and at least one key was deleted
        if (deleted > 0 && aofEnabled) {
            aof.append(args);
        }

        // Return number of deleted keys as integer
        writeInteger(connection, deleted);
    }

    private void writeInteger(Connection connection, int value) {
        byte[] valueBytes = String.valueOf(value).getBytes();
        byte[] response = new byte[1 + valueBytes.length + 2];

        int pos = 0;
        response[pos++] = ':';
        System.arraycopy(valueBytes, 0, response, pos, valueBytes.length);
        pos += valueBytes.length;
        response[pos++] = '\r';
        response[pos] = '\n';

        connection.write(response);
    }
}
