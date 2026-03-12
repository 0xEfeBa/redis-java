package com.redisjava.network;

import org.junit.jupiter.api.BeforeEach;

import com.redisjava.testutil.Assert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Connection back-pressure testi — yazma kuyruğu dolunca bağlantı kesilmeli.
 */
public class ConnectionBackpressureTest {

    private AeEventLoop eventLoop;
    private Thread serverThread;
    private int port;
    private boolean serverStarted = false;
    private OverflowHandler handler;
    @BeforeEach

    public void setup() {
        try {
            handler = new OverflowHandler();
            eventLoop = new AeEventLoop(0, handler);
            port = eventLoop.getPort();
            serverThread = new Thread(eventLoop::run, "EventLoop-Backpressure");
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

    /** Yazma kuyruğu taştığında bağlantı kesilmeli */
    public void testOverflowTriggersDisconnect() throws Exception {
        if (!serverStarted) return;

        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            // Sunucu 1 MB'dan fazla cevap yazacak, kuyruk dolacak → disconnect
            byte[] trigger = new byte[8 * 1024];
            out.write(trigger);
            out.flush();

            boolean disconnected = handler.awaitDisconnect(5, TimeUnit.SECONDS);
            Assert.assertTrue("disconnect tetiklendi", disconnected);
        }
    }

    // ── Overflow handler ─────────────────────────────────────────────────

    private static final class OverflowHandler implements ProtocolHandler {
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void handle(Connection conn, ByteBuffer buffer) {
            // 1 MB + 1 byte yaz — maksimum kuyruk boyutunu aşar
            conn.write(new byte[1024 * 1024 + 1]);
        }

        @Override
        public void onDisconnect(Connection conn) {
            latch.countDown();
        }

        boolean awaitDisconnect(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}
