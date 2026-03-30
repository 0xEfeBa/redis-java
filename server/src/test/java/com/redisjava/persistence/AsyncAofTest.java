package com.redisjava.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
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

    @BeforeEach
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
        assertEquals(1, queue.size());
        byte[] got = queue.poll();
        assertNotNull(got);
        assertEquals("hello", new String(got));
        assertTrue(queue.isEmpty(), "queue empty after poll");
    }

    /** Queue tracks pendingBytes */
    @Test
    public void testQueue_pendingBytes() {
        queue.offer("abc".getBytes());   // 3 bytes
        queue.offer("de".getBytes());    // 2 bytes
        assertEquals(5L, queue.getPendingBytes());
        queue.poll();
        assertEquals(2L, queue.getPendingBytes());
        queue.poll();
        assertEquals(0L, queue.getPendingBytes());
    }

    /** poll on empty queue returns null */
    @Test
    public void testQueue_pollEmpty_returnsNull() {
        assertNull(queue.poll());
    }

    /** reset clears all entries */
    @Test
    public void testQueue_reset() {
        queue.offer("x".getBytes());
        queue.offer("y".getBytes());
        queue.reset();
        assertTrue(queue.isEmpty(), "empty after reset");
        assertEquals(0L, queue.getPendingBytes());
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
        assertTrue(resp.startsWith("*3\r\n"), "array header");
        assertTrue(resp.contains("$3\r\nSET\r\n"), "SET bulk");
        assertTrue(resp.contains("$5\r\nmykey\r\n"), "key bulk");
        assertTrue(resp.contains("$5\r\nmyval\r\n"), "val bulk");
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
        assertTrue(resp.startsWith("*2\r\n"), "array header *2");
        assertTrue(resp.contains("DEL"), "DEL present");
        assertTrue(resp.contains("$1\r\nk\r\n"), "key k present");
    }

    /** null / empty args returns null */
    @Test
    public void testSerialize_nullArgs_returnsNull() {
        assertNull(AofManager.serializeCommand(null));
        assertNull(AofManager.serializeCommand(new RespToken[0]));
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
        assertTrue(fileContent.contains("*3"), "file has RESP array");
        assertTrue(fileContent.contains("SET"), "file has SET");
        assertTrue(fileContent.contains("foo"), "file has foo");
        assertTrue(fileContent.contains("bar"), "file has bar");
        assertEquals(1L, writer.getTotalWritten());
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
        assertTrue(content.contains("k1"), "k1 present");
        assertTrue(content.contains("k2"), "k2 present");
        assertTrue(content.contains("k3"), "k3 present");
        assertEquals(3L, writer.getTotalWritten());
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
        assertTrue(content.contains("LPUSH"), "LPUSH written");
        assertTrue(content.contains("list"), "list key written");
        Files.deleteIfExists(bgFile);
    }

    /** Queue is empty after full drain */
    public void testWriter_queueEmptyAfterDrain() throws Exception {
        queue.reset();
        AofWriterThread writer = new AofWriterThread(queue, tmpFile);

        for (int i = 0; i < 10; i++) {
            queue.offer(("entry" + i + "\r\n").getBytes());
        }
        assertEquals(10, queue.size());

        writer.drainNow();

        assertTrue(queue.isEmpty(), "queue empty");
        assertEquals(10L, writer.getTotalWritten());
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
        assertEquals(1, queue.size());

        // Drain to file and verify
        AofWriterThread writer = new AofWriterThread(queue, p);
        writer.drainNow();
        // The entry was appended to the already-open file channel by AofManager.open()
        // The async entry is written by the writer thread to the same path
        assertEquals(0, queue.size());

        AofManager.getInstance().close();
        Files.deleteIfExists(p);
    }

    // ── Main ───────────────────────────────────────────────────────────────
}
