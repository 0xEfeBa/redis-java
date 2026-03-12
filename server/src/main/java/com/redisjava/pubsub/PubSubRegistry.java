package com.redisjava.pubsub;

import com.redisjava.network.Connection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pub/Sub channel registry.
 *
 * <p>Maintains two maps for O(1) subscribe/unsubscribe/publish:
 * <ol>
 *   <li>channel → Set&lt;Connection&gt; (fan-out during PUBLISH)</li>
 *   <li>Connection → Set&lt;BytesKey&gt; (fast unsubscribe-all)</li>
 * </ol>
 *
 * <p>All operations run on the single-threaded event loop, so no
 * synchronisation is required.
 */
public class PubSubRegistry {

    // ── Singleton ──────────────────────────────────────────────────────────

    private static final PubSubRegistry INSTANCE = new PubSubRegistry();

    public static PubSubRegistry getInstance() {
        return INSTANCE;
    }

    // ── State ──────────────────────────────────────────────────────────────

    /** channel → subscriber connections */
    private final Map<BytesKey, Set<Connection>> channels = new HashMap<>();

    /** connection → subscribed channel keys (reverse index) */
    private final Map<Connection, Set<BytesKey>> subscriptions = new HashMap<>();

    // ── API ────────────────────────────────────────────────────────────────

    /**
     * Subscribes a connection to a channel.
     *
     * @param conn    The subscriber connection.
     * @param channel Channel name bytes.
     * @return Total number of channels this connection is now subscribed to.
     */
    public int subscribe(Connection conn, byte[] channel) {
        BytesKey key = new BytesKey(channel);

        Set<Connection> subs = channels.computeIfAbsent(key, k -> new HashSet<>());
        subs.add(conn);

        Set<BytesKey> connChans = subscriptions.computeIfAbsent(conn, c -> new HashSet<>());
        connChans.add(key);

        return connChans.size();
    }

    /**
     * Unsubscribes a connection from a channel.
     *
     * @param conn    The subscriber connection.
     * @param channel Channel name bytes.
     * @return Total number of channels this connection is now subscribed to.
     */
    public int unsubscribe(Connection conn, byte[] channel) {
        BytesKey key = new BytesKey(channel);

        Set<Connection> subs = channels.get(key);
        if (subs != null) {
            subs.remove(conn);
            if (subs.isEmpty()) {
                channels.remove(key);
            }
        }

        Set<BytesKey> connChans = subscriptions.get(conn);
        if (connChans != null) {
            connChans.remove(key);
            if (connChans.isEmpty()) {
                subscriptions.remove(conn);
            }
        }

        return connChans == null ? 0 : connChans.size();
    }

    /**
     * Returns all channels to which the connection is subscribed.
     *
     * @param conn The connection.
     * @return List of channel name byte arrays (never null, may be empty).
     */
    public List<byte[]> getSubscriptions(Connection conn) {
        Set<BytesKey> keys = subscriptions.get(conn);
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        List<byte[]> result = new ArrayList<>(keys.size());
        for (BytesKey k : keys) {
            result.add(k.bytes);
        }
        return result;
    }

    /**
     * Publishes a message to all subscribers of a channel.
     *
     * <p>Message format (RESP push array):
     * {@code *3\r\n$7\r\nmessage\r\n$<cn>\r\n<channel>\r\n$<mn>\r\n<message>\r\n}
     *
     * @param channel Channel name bytes.
     * @param message Message bytes.
     * @return Number of subscribers that received the message.
     */
    public int publish(byte[] channel, byte[] message) {
        BytesKey key = new BytesKey(channel);
        Set<Connection> subs = channels.get(key);
        if (subs == null || subs.isEmpty()) {
            return 0;
        }

        byte[] frame = buildMessageFrame(channel, message);
        int count = 0;
        // Collect in array first to avoid ConcurrentModificationException if a
        // subscriber disconnects during fan-out (not an issue in single-threaded
        // event loop, but defensive practice).
        Connection[] subscribers = subs.toArray(new Connection[0]);
        for (Connection sub : subscribers) {
            if (!sub.isClosed()) {
                sub.write(frame);
                count++;
            }
        }
        return count;
    }

    /**
     * Removes a connection from all channels it is subscribed to.
     * Called on disconnect.
     *
     * @param conn The disconnected connection.
     */
    public void removeAll(Connection conn) {
        Set<BytesKey> keys = subscriptions.remove(conn);
        if (keys == null) return;
        for (BytesKey key : keys) {
            Set<Connection> subs = channels.get(key);
            if (subs != null) {
                subs.remove(conn);
                if (subs.isEmpty()) {
                    channels.remove(key);
                }
            }
        }
    }

    /**
     * Returns the number of connections subscribed to a channel.
     *
     * @param channel Channel name bytes.
     * @return Subscriber count.
     */
    public int subscriberCount(byte[] channel) {
        Set<Connection> subs = channels.get(new BytesKey(channel));
        return subs == null ? 0 : subs.size();
    }

    /**
     * Returns total number of active channels (channels with at least one subscriber).
     */
    public int channelCount() {
        return channels.size();
    }

    /**
     * Clears all subscriptions. <strong>For testing only.</strong>
     */
    public void reset() {
        channels.clear();
        subscriptions.clear();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static final byte[] MESSAGE_KEYWORD = "message".getBytes();

    /**
     * Builds a RESP3-style push array for a pub/sub message:
     * {@code *3\r\n$7\r\nmessage\r\n$<cn>\r\n<channel>\r\n$<mn>\r\n<msg>\r\n}
     */
    public static byte[] buildMessageFrame(byte[] channel, byte[] message) {
        StringBuilder sb = new StringBuilder();
        sb.append("*3\r\n");
        sb.append('$').append(MESSAGE_KEYWORD.length).append("\r\n");
        sb.append(new String(MESSAGE_KEYWORD)).append("\r\n");
        sb.append('$').append(channel.length).append("\r\n");
        sb.append(new String(channel)).append("\r\n");
        sb.append('$').append(message.length).append("\r\n");
        sb.append(new String(message)).append("\r\n");
        return sb.toString().getBytes();
    }

    /**
     * Builds the confirmation array for SUBSCRIBE / UNSUBSCRIBE:
     * {@code *3\r\n$<kw-len>\r\n<keyword>\r\n$<cn>\r\n<channel>\r\n:<count>\r\n}
     */
    public static byte[] buildConfirmFrame(byte[] keyword, byte[] channel, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("*3\r\n");
        sb.append('$').append(keyword.length).append("\r\n");
        sb.append(new String(keyword)).append("\r\n");
        sb.append('$').append(channel.length).append("\r\n");
        sb.append(new String(channel)).append("\r\n");
        sb.append(':').append(count).append("\r\n");
        return sb.toString().getBytes();
    }

    // ── Inner type ─────────────────────────────────────────────────────────

    /**
     * Byte-array wrapper with proper hashCode / equals so it can be used
     * as a Map key.
     */
    public static final class BytesKey {
        final byte[] bytes;

        BytesKey(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BytesKey)) return false;
            return Arrays.equals(bytes, ((BytesKey) o).bytes);
        }
    }
}
