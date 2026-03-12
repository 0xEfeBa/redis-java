package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * ECHO command implementation.
 * <p>
 * Returns the given argument as a bulk string.
 * Usage: ECHO message
 * </p>
 */
public class EchoCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        if (args.length < 2) {
            connection.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] message = args[1].getData();
        writeBulkString(connection, message);
    }

    private void writeBulkString(Connection connection, byte[] data) {
        // Format: $length\r\ndata\r\n
        byte[] lengthBytes = String.valueOf(data.length).getBytes();

        int totalSize = 1 + lengthBytes.length + 2 + data.length + 2;
        byte[] response = new byte[totalSize];

        int pos = 0;
        response[pos++] = Responses.BULK_PREFIX;
        System.arraycopy(lengthBytes, 0, response, pos, lengthBytes.length);
        pos += lengthBytes.length;
        response[pos++] = '\r';
        response[pos++] = '\n';
        System.arraycopy(data, 0, response, pos, data.length);
        pos += data.length;
        response[pos++] = '\r';
        response[pos] = '\n';

        connection.write(response);
    }
}
