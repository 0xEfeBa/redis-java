package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;
import com.redisjava.pubsub.PubSubRegistry;
import com.redisjava.protocol.RespToken;

/**
 * Tests for SUBSCRIBE / UNSUBSCRIBE / PUBLISH commands.
 */
public class PubSubTest {

    private SubscribeCommand   subscribe;
    private UnsubscribeCommand unsubscribe;
    private PublishCommand     publish;

    // Fresh connections for each test
    private MockConnection conn1;
    private MockConnection conn2;
    @BeforeEach

    public void setup() {
        // Clear singleton registry state so each test starts clean
        PubSubRegistry.getInstance().reset();

        subscribe   = new SubscribeCommand();
        unsubscribe = new UnsubscribeCommand();
        publish     = new PublishCommand();
        conn1 = new MockConnection();
        conn2 = new MockConnection();
    }

    // ── SUBSCRIBE ──────────────────────────────────────────────────────────

    /** SUBSCRIBE sends a 3-element array back for each channel */
    @Test
    public void testSubscribe_singleChannel_sendsConfirmation() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "news"));

        String resp = conn1.getLastResponse();
        Assert.assertTrue("starts with *3", resp.startsWith("*3\r\n"));
        Assert.assertTrue("contains 'subscribe' keyword", resp.contains("subscribe"));
        Assert.assertTrue("contains channel name", resp.contains("news"));
        Assert.assertTrue("contains count :1", resp.contains(":1\r\n"));
    }

    /** Subscribe to two channels — two confirmations, increasing count */
    @Test
    public void testSubscribe_multipleChannels_countIncreases() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "a", "b"));

        String resp = conn1.getLastResponse();
        // First channel → count 1, second → count 2
        Assert.assertTrue("count 1 present", resp.contains(":1\r\n"));
        Assert.assertTrue("count 2 present", resp.contains(":2\r\n"));
    }

    /** Subscribing twice to the same channel should be idempotent */
    @Test
    public void testSubscribe_duplicate_idempotent() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "dup"));
        conn1.clear();
        subscribe.execute(conn1, tokens("SUBSCRIBE", "dup"));

        String resp = conn1.getLastResponse();
        // Still subscribed to exactly 1 channel
        Assert.assertTrue("count remains 1", resp.contains(":1\r\n"));
    }

    /** SUBSCRIBE with no channel arg returns error */
    @Test
    public void testSubscribe_noArgs_returnsError() {
        subscribe.execute(conn1, tokens("SUBSCRIBE"));
        String resp = conn1.getLastResponse();
        Assert.assertTrue("error response", resp.startsWith("-"));
    }

    // ── PUBLISH ────────────────────────────────────────────────────────────

    /** PUBLISH to a subscribed channel delivers message to subscriber */
    @Test
    public void testPublish_deliveredToSubscriber() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "sports"));
        conn1.clear();

        publish.execute(conn2, tokens("PUBLISH", "sports", "goal!"));

        // conn2 receives the count (1 subscriber)
        Assert.assertTrue("publisher gets :1", conn2.getLastResponse().contains(":1\r\n"));

        // conn1 receives the push message
        String push = conn1.getLastResponse();
        Assert.assertTrue("push is *3 array", push.startsWith("*3\r\n"));
        Assert.assertTrue("push contains 'message'", push.contains("message"));
        Assert.assertTrue("push contains channel", push.contains("sports"));
        Assert.assertTrue("push contains payload", push.contains("goal!"));
    }

    /** PUBLISH to a channel with no subscribers returns :0 */
    @Test
    public void testPublish_noSubscribers_returnsZero() {
        publish.execute(conn1, tokens("PUBLISH", "ghost-channel", "hello"));
        Assert.assertTrue("returns :0", conn1.getLastResponse().contains(":0\r\n"));
    }

    /** PUBLISH delivers to multiple subscribers */
    @Test
    public void testPublish_multipleSubscribers() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "events"));
        subscribe.execute(conn2, tokens("SUBSCRIBE", "events"));
        conn1.clear();
        conn2.clear();

        MockConnection publisher = new MockConnection();
        publish.execute(publisher, tokens("PUBLISH", "events", "ping"));

        Assert.assertTrue("publisher sees :2", publisher.getLastResponse().contains(":2\r\n"));
        Assert.assertTrue("conn1 got message", conn1.getLastResponse().contains("ping"));
        Assert.assertTrue("conn2 got message", conn2.getLastResponse().contains("ping"));
    }

    /** PUBLISH with wrong arg count returns error */
    @Test
    public void testPublish_wrongArgCount_returnsError() {
        publish.execute(conn1, tokens("PUBLISH", "chan"));
        Assert.assertTrue("error", conn1.getLastResponse().startsWith("-"));
    }

    // ── UNSUBSCRIBE ────────────────────────────────────────────────────────

    /** UNSUBSCRIBE from a specific channel */
    @Test
    public void testUnsubscribe_specificChannel() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "x", "y"));
        conn1.clear();

        unsubscribe.execute(conn1, tokens("UNSUBSCRIBE", "x"));

        String resp = conn1.getLastResponse();
        Assert.assertTrue("contains 'unsubscribe' keyword", resp.contains("unsubscribe"));
        Assert.assertTrue("channel x mentioned", resp.contains("x"));
        // After removing 'x', still subscribed to 'y' → count = 1
        Assert.assertTrue("count is 1", resp.contains(":1\r\n"));
    }

    /** UNSUBSCRIBE with no args removes all subscriptions */
    @Test
    public void testUnsubscribe_noArgs_removesAll() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "a", "b", "c"));
        conn1.clear();

        unsubscribe.execute(conn1, tokens("UNSUBSCRIBE"));

        // After full unsubscribe, PUBLISH should deliver 0 messages
        MockConnection pub = new MockConnection();
        publish.execute(pub, tokens("PUBLISH", "a", "hi"));
        Assert.assertTrue("no messages delivered to a", pub.getLastResponse().contains(":0\r\n"));
    }

    /** UNSUBSCRIBE when not subscribed sends null-channel confirmation */
    @Test
    public void testUnsubscribe_notSubscribed_sendsNull() {
        unsubscribe.execute(conn1, tokens("UNSUBSCRIBE"));
        String resp = conn1.getLastResponse();
        Assert.assertTrue("null-channel confirmation", resp.contains("unsubscribe"));
        Assert.assertTrue("count zero", resp.contains(":0\r\n"));
    }

    /** After unsubscribe, no more messages are delivered */
    @Test
    public void testUnsubscribe_stopsDelivery() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "news2"));
        conn1.clear();
        unsubscribe.execute(conn1, tokens("UNSUBSCRIBE", "news2"));
        conn1.clear();

        MockConnection pub = new MockConnection();
        publish.execute(pub, tokens("PUBLISH", "news2", "breaking"));

        // conn1 should receive nothing after unsubscribing
        Assert.assertNull(conn1.getLastResponse());
        Assert.assertTrue("publisher sees :0", pub.getLastResponse().contains(":0\r\n"));
    }

    // ── Registry ───────────────────────────────────────────────────────────

    /** subscriberCount returns correct value */
    @Test
    public void testRegistry_subscriberCount() {
        PubSubRegistry registry = PubSubRegistry.getInstance();
        byte[] chan = "counter-chan".getBytes();

        subscribe.execute(conn1, tokens("SUBSCRIBE", "counter-chan"));
        Assert.assertEquals(1, registry.subscriberCount(chan));

        subscribe.execute(conn2, tokens("SUBSCRIBE", "counter-chan"));
        Assert.assertEquals(2, registry.subscriberCount(chan));

        unsubscribe.execute(conn1, tokens("UNSUBSCRIBE", "counter-chan"));
        Assert.assertEquals(1, registry.subscriberCount(chan));
    }

    /** removeAll removes all channels for a connection */
    @Test
    public void testRegistry_removeAll() {
        subscribe.execute(conn1, tokens("SUBSCRIBE", "r1", "r2", "r3"));
        PubSubRegistry.getInstance().removeAll(conn1);

        MockConnection pub = new MockConnection();
        publish.execute(pub, tokens("PUBLISH", "r1", "msg"));
        Assert.assertTrue("no receivers after removeAll", pub.getLastResponse().contains(":0\r\n"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Build a tokens array as the command dispatcher would pass to execute(). */
    private static RespToken[] tokens(String... parts) {
        RespToken[] bulks = new RespToken[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bulks[i] = RespToken.bulkString(parts[i].getBytes());
        }
        return bulks;
    }

    // ── Main ───────────────────────────────────────────────────────────────
}
