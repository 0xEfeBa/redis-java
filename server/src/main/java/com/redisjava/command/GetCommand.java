package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.datastruct.RString;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.stats.ServerStats;

/**
 * GET command implementation.
 * <p>
 * GET key - retrieves the value for a key.
 * </p>
 */
public class GetCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        // GET key (minimum 2 args: command + key)
        if (args.length < 2) {
            connection.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key = args[1].getData();
        if (key == null) {
            connection.write(Responses.ERR_SYNTAX);
            return;
        }

        RedisObject value = Db.getInstance().getObject(key);
        if (value == null) {
            ServerStats.getInstance().keyMiss();
            connection.write(Responses.NULL_BULK);
            return;
        }

        if (value.getType() != RedisObject.Type.STRING) {
            ServerStats.getInstance().keyMiss();
            connection.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        ServerStats.getInstance().keyHit();
        RString str = (RString) value.getValue();
        writeBulkString(connection, str);
    }

    private void writeBulkString(Connection connection, RString value) {
        int length = value.getLength();
        byte[] lengthBytes = String.valueOf(length).getBytes();

        byte[] header = new byte[1 + lengthBytes.length + 2];
        int pos = 0;
        header[pos++] = Responses.BULK_PREFIX;
        System.arraycopy(lengthBytes, 0, header, pos, lengthBytes.length);
        pos += lengthBytes.length;
        header[pos++] = '\r';
        header[pos] = '\n';

        connection.write(header);
        connection.writeFromAddress(value.getAddress(), length);
        connection.write(Responses.CRLF);
    }
}
