package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.pubsub.PubSubRegistry;

/**
 * SUBSCRIBE channel [channel ...]
 *
 * <p>Registers the connection as a subscriber for each listed channel.
 * For every channel, a confirmation array is sent back:
 * {@code *3\r\n$9\r\nsubscribe\r\n$<cn>\r\n<channel>\r\n:<count>\r\n}
 * where {@code <count>} is the total number of channels this client
 * is now subscribed to.
 */
public class SubscribeCommand implements Command {

    private static final byte[] SUBSCRIBE_KW = "subscribe".getBytes();

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 2) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        PubSubRegistry registry = PubSubRegistry.getInstance();
        for (int i = 1; i < args.length; i++) {
            byte[] channel = args[i].getData();
            int count = registry.subscribe(conn, channel);
            conn.write(PubSubRegistry.buildConfirmFrame(SUBSCRIBE_KW, channel, count));
        }
    }
}
