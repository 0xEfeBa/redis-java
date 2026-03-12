package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.pubsub.PubSubRegistry;

/**
 * PUBLISH channel message
 *
 * <p>Sends {@code message} to all connections subscribed to {@code channel}.
 * Returns the number of subscribers that received the message as a RESP integer.
 */
public class PublishCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length != 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] channel = args[1].getData();
        byte[] message = args[2].getData();

        int count = PubSubRegistry.getInstance().publish(channel, message);
        conn.writeInteger(count);
    }
}
