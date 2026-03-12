package com.redisjava.persistence;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;
import com.redisjava.protocol.RespToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for AofQueue + AofWriterThread (Async AOF pipeline).
 */
public class AsyncAofTest {

    private Path tmpFile;
    private AofQueue queue;

    public void setup() throws IOException {
        tmpFile = Files.createTempFile("async-aof-test-", ".aof");
        queue   = AofQueue.getInstance();
        queue.reset();
    }

    // ── AofQueue ───────────────────────────────────────────────────────────

    /** Offer and poll round-trip */
    @Test
    public void testQueue_offerPoll() {
        byte[] data = "hello".getBytes();
        queue.offer(data);
        Assert.assertEquals(1, queue.size());
        byte[] got = queue.poll();
        Assert.assertNotNull(got);
        Assert.assertEquals("hello", new String(got));
        Assert.assertTrue("queue empty after poll", queue.isEmpty());
    }

    /** Queue tracks pendingBytes */
    @Test
    public void testQueue_pendingBytes() {
        queue.offer("abc".getBytes());   // 3 bytes
        queue.offer("de".getBytes());    // 2 bytes
        Assert.assertEquals(5L, queue.getPendingBytes());
        queue.poll();
        Assert.assertEquals(2L, queue.getPendingBytes());
        queue.poll();
        Assert.assertEquals(0L, queue.getPendingBytes());
    }

    /** poll on empty queue returns null */
    @Test
    public void testQueue_pollEmpty_returnsNull() {
        Assert.assertNull(queue.poll());
    }

    /** reset clears all entries */
    @Test
    public void testQueue_reset() {
        queue.offer("x".getBytes());
        queue.offer("y".getBytes());
        queue.reset();
        Assert.assertTrue("empty after reset", queue.isEmpty());
        Assert.assertEquals(0L, queue.getPendingBytes());
    }

    // ── AofManager.serializeCommand ────────────────────────────────────────

    /** SET key value serialises to RESP array */
    @Test
    public void testSerialize_setCommand() {
        RespToken[] args = {
            RespToken.bulkString("SET".getBytes()),
            RespToken.bulkString("mykey".getBytes()),
            RespToken.bulkString("myval".getBytes())
        };
        byte[] bytes = AofManager.serializeCommand(args);
        String resp = new String(bytes);
        Assert.assertTrue("array header", resp.startsWith("*3\r\n"));
        Assert.assertTrue("SET bulk", resp.contains("$3\r\nSET\r\n"));
        Assert.assertTrue("key bulk", resp.contains("$5\r\nmykey\r\n"));
        Assert.assertTrue("val bulk", resp.contains("$5\r\nmyval\r\n"));
    }

    /** DEL key serialises correctly */
    @Test
    public void testSerialize_delCommand() {
        RespToken[] args = {
            RespToken.bulkString("DEL".getBytes()),
            RespToken.bulkString("k".getBytes())
        };
        byte[] bytes = AofManager.serializeCommand(args);
        String resp = new String(bytes);
        Assert.assertTrue("array header *2", resp.startsWith("*2\r\n"));
        Assert.assertTrue("DEL present", resp.contains("DEL"));
        Assert.assertTrue("key k present", resp.contains("$1\r\nk\r\n"));
    }

    /** null / empty args returns null */
    @Test
    public void testSerialize_nullArgs_returnsNull() {
        Assert.assertNull(AofManager.serializeCommand(null));
        Assert.assertNull(AofManager.serializeCommand(new RespToken[0]));
    }

    // ── AofWriterThread ────────────────────────────────────────────────────

    /** Writer drainNow writes queued bytes to disk */
    public void testWriter_drainNow_writesToDisk() throws Exception {
        queue.reset();
        AofWriterThread writer = new AofWriterThread(queue, tmpFile);

        RespToken[] cmd = {
            RespToken.bulkString("SET".getBytes()),
            RespToken.bulkString("foo".getBytes()),
            RespToken.bulkString("bar".getBytes())
        };
        queue.offer(AofManager.serializeCommand(cmd));

        writer.drainNow();

        String fileContent = new String(Files.readAllBytes(tmpFile));
        Assert.assertTrue("file has RESP array", fileContent.contains("*3"));
        Assert.assertTrue("file has SET", fileContent.contains("SET"));
        Assert.assertTrue("file has foo", fileContent.contains("foo"));
        Assert.assertTrue("file has bar", fileContent.contains("bar"));
        Assert.assertEquals(1L, writer.getTotalWritten());
    }

    /** Multiple entries are all written */
    public void testWriter_multipleEntries() throws Exception {
        queue.reset();
        AofWriterThread writer = new AofWriterThread(queue, tmpFile);

        String[] keys = {"k1", "k2", "k3"};
        for (String k : keys) {
            RespToken[] cmd = {
                RespToken.bulkString("SET".getBytes()),
                RespToken.bulkString(k.getBytes()),
                RespToken.bulkString("v".getBytes())
            };
            queue.offer(AofManager.serializeCommand(cmd));
        }

        writer.drainNow();

        String content = new String(Files.readAllBytes(tmpFile));
        Assert.assertTrue("k1 present", content.contains("k1"));
        Assert.assertTrue("k2 present", content.contains("k2"));
        Assert.assertTrue("k3 present", content.contains("k3"));
        Assert.assertEquals(3L, writer.getTotalWritten());
    }

    /** Writer thread starts, receives entry, and drains within timeout */
    public void testWriter_backgroundThread_drains() throws Exception {
        Path bgFile = Files.createTempFile("async-aof-bg-", ".aof");
        queue.reset();
        AofWriterThread writer = new AofWriterThread(queue, bgFile);
        writer.start();

        RespToken[] cmd = {
            RespToken.bulkString("LPUSH".getBytes()),
            RespToken.bulkString("list".getBytes()),
            RespToken.bulkString("val".getBytes())
        };
        queue.offer(AofManager.serializeCommand(cmd));

        // Wait for the background thread to drain (up to 200ms)
        long deadline = System.currentTimeMillis() + 200;
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        writer.shutdown();
        writer.join(1000);

        String content = new String(Files.readAllBytes(bgFile));
        Assert.assertTrue("LPUSH written", content.contains("LPUSH"));
        Assert.assertTrue("list key written", content.contains("list"));
        Files.deleteIfExists(bgFile);
    }

    /** Queue is empty after full drain */
    public void testWriter_queueEmptyAfterDrain() throws Exception {
        queue.reset();
        AofWriterThread writer = new AofWriterThread(queue, tmpFile);

        for (int i = 0; i < 10; i++) {
            queue.offer(("entry" + i + "\r\n").getBytes());
        }
        Assert.assertEquals(10, queue.size());

        writer.drainNow();

        Assert.assertTrue("queue empty", queue.isEmpty());
        Assert.assertEquals(10L, writer.getTotalWritten());
    }

    /** appendAsync enqueues entry in AofQueue when AOF is enabled */
    public void testAofManager_appendAsync_enqueues() throws Exception {
        queue.reset();
        Path p = Files.createTempFile("aof-mgr-async-", ".aof");
        AofManager.init(p);
        AofManager.getInstance().open();

        RespToken[] args = {
            RespToken.bulkString("SET".getBytes()),
            RespToken.bulkString("ak".getBytes()),
            RespToken.bulkString("av".getBytes())
        };
        AofManager.getInstance().appendAsync(args);
        Assert.assertEquals(1, queue.size());

        // Drain to file and verify
        AofWriterThread writer = new AofWriterThread(queue, p);
        writer.drainNow();
        // The entry was appended to the already-open file channel by AofManager.open()
        // The async entry is written by the writer thread to the same path
        Assert.assertEquals(0, queue.size());

        AofManager.getInstance().close();
        Files.deleteIfExists(p);
    }

    // ── Main ───────────────────────────────────────────────────────────────
}
