package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * PING command implementation.
 * <p>
 * Returns +PONG when called without arguments,
 * or echoes the argument if provided.
 * </p>
 */
public class PingCommand implements Command {

    @Override
    public void execute(Connection connection, RespToken[] args) {
        if (args.length > 1) {
            // PING with argument - echo it back as bulk string
            byte[] message = args[1].getData();
            writeBulkString(connection, message);
        } else {
            // Simple PING - return PONG
            connection.write(Responses.PONG);
        }
    }

    private void writeBulkString(Connection connection, byte[] data) {
        // Format: $length\r\ndata\r\n
        byte[] lengthBytes = String.valueOf(data.length).getBytes();

        // Calculate total size
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
