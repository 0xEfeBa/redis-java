package com.redisjava.command;

import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.pubsub.PubSubRegistry;

import java.util.List;

/**
 * UNSUBSCRIBE [channel ...]
 *
 * <p>Unsubscribes the connection from one or more channels.
 * If no channels are specified, unsubscribes from all channels
 * the connection is currently subscribed to.
 *
 * <p>For every unsubscribed channel, a confirmation array is sent:
 * {@code *3\r\n$11\r\nunsubscribe\r\n$<cn>\r\n<channel>\r\n:<count>\r\n}
 *
 * <p>If the connection has no subscriptions and no channels were specified,
 * a single {@code *3\r\n$11\r\nunsubscribe\r\n$-1\r\n\r\n:0\r\n} is sent
 * (null-channel variant, matching real Redis behaviour).
 */
public class UnsubscribeCommand implements Command {

    private static final byte[] UNSUBSCRIBE_KW = "unsubscribe".getBytes();

    @Override
    public void execute(Connection conn, RespToken[] args) {
        PubSubRegistry registry = PubSubRegistry.getInstance();

        if (args.length >= 2) {
            // Explicit channel list
            for (int i = 1; i < args.length; i++) {
                byte[] channel = args[i].getData();
                int count = registry.unsubscribe(conn, channel);
                conn.write(PubSubRegistry.buildConfirmFrame(UNSUBSCRIBE_KW, channel, count));
            }
        } else {
            // No args → unsubscribe from everything
            List<byte[]> subs = registry.getSubscriptions(conn);
            if (subs.isEmpty()) {
                // Nothing to unsubscribe — send null-channel confirmation
                conn.write("*3\r\n$11\r\nunsubscribe\r\n$-1\r\n\r\n:0\r\n".getBytes());
            } else {
                // Snapshot: getSubscriptions returns a fresh list, safe to iterate
                for (byte[] channel : subs) {
                    int count = registry.unsubscribe(conn, channel);
                    conn.write(PubSubRegistry.buildConfirmFrame(UNSUBSCRIBE_KW, channel, count));
                }
            }
        }
    }
}
