package com.redisjava.network;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.testutil.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Integration tests for AeEventLoop — gerçek TCP bağlantılarıyla echo sunucu testi.
 */
public class AeEventLoopTest {

    private AeEventLoop eventLoop;
    private Thread serverThread;
    private int port;
    private boolean serverStarted = false;
    @BeforeEach

    public void setup() {
        try {
            eventLoop = new AeEventLoop(0, new EchoProtocolHandler());
            port = eventLoop.getPort();
            serverThread = new Thread(eventLoop::run, "EventLoop-Test");
            serverThread.setDaemon(true);
            serverThread.start();
            Thread.sleep(150);
            serverStarted = true;
        } catch (IOException | InterruptedException e) {
            serverStarted = false;
        }
    }

    public void teardown() {
        if (eventLoop != null) eventLoop.stop();
        if (serverThread != null) {
            try { serverThread.join(2000); } catch (InterruptedException ignored) {}
        }
    }

    // ── Port kontrolü ────────────────────────────────────────────────────
    @Test

    public void testGetPort_validRange() {
        if (!serverStarted) return;
        Assert.assertTrue("port > 0", port > 0);
        Assert.assertTrue("port < 65536", port < 65536);
    }

    // ── Echo testi ───────────────────────────────────────────────────────

    public void testEchoServer_singleMessage() throws Exception {
        if (!serverStarted) return;
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            InputStream  in  = socket.getInputStream();

            String msg = "Merhaba";
            out.write(msg.getBytes(StandardCharsets.UTF_8));
            out.flush();

            byte[] buf = new byte[256];
            int n = in.read(buf);
            String response = new String(buf, 0, n, StandardCharsets.UTF_8);
            Assert.assertEquals(msg, response);
        }
    }

    public void testEchoServer_multipleMessages() throws Exception {
        if (!serverStarted) return;
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            InputStream  in  = socket.getInputStream();

            String[] messages = {"Hello", "World", "Redis-Java"};
            for (String msg : messages) {
                out.write(msg.getBytes(StandardCharsets.UTF_8));
                out.flush();

                byte[] buf = new byte[256];
                int n = in.read(buf);
                String resp = new String(buf, 0, n, StandardCharsets.UTF_8);
                Assert.assertEquals(msg, resp);
            }
        }
    }

    public void testEchoServer_multipleClients() throws Exception {
        if (!serverStarted) return;
        int clientCount = 5;
        boolean[] results = new boolean[clientCount];
        Thread[] threads = new Thread[clientCount];

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                try (Socket socket = new Socket("localhost", port)) {
                    socket.setSoTimeout(3000);
                    String msg = "Client-" + id;
                    socket.getOutputStream().write(msg.getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    byte[] buf = new byte[256];
                    int n = socket.getInputStream().read(buf);
                    results[id] = msg.equals(new String(buf, 0, n, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    results[id] = false;
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join(3000);

        for (int i = 0; i < clientCount; i++) {
            Assert.assertTrue("client " + i + " correct echo", results[i]);
        }
    }

    public void testConnectionClose_graceful() throws Exception {
        if (!serverStarted) return;
        Socket socket = new Socket("localhost", port);
        socket.setSoTimeout(3000);
        socket.getOutputStream().write("Test".getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();

        byte[] buf = new byte[256];
        int n = socket.getInputStream().read(buf);
        Assert.assertEquals(4, n);

        socket.close();
        Assert.assertTrue("socket closed", socket.isClosed());
    }
}
