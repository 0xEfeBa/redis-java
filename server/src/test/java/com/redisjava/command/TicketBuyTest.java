package com.redisjava.command;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import com.redisjava.datastruct.Db;
import com.redisjava.memory.MemoryManager;
import com.redisjava.protocol.RedisProtocolHandler;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

/**
 * Tests for TICKET.BUY command.
 */
public class TicketBuyTest {

    private RedisProtocolHandler handler;
    private MockConnection conn;
    @BeforeEach

    public void setup() {
        Db.init(new MemoryManager(4));
        handler = new RedisProtocolHandler();
        conn = new MockConnection();
    }

    private static String cmd(String... parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(parts.length).append("\r\n");
        for (String p : parts) {
            sb.append("$").append(p.length()).append("\r\n").append(p).append("\r\n");
        }
        return sb.toString();
    }

    private String exec(String... parts) {
        conn.clear();
        handler.handle(conn, ByteBuffer.wrap(cmd(parts).getBytes()));
        return conn.getLastResponse();
    }

    // Setup helpers
    private void addToQueue(String key, String user, double score) {
        exec("ZADD", key, String.valueOf((long) score), user);
    }

    private void setTickets(String key, int count) {
        exec("SET", key, String.valueOf(count));
    }

    // ── Happy path ─────────────────────────────────────────────────────────
    @Test

    public void testBuy_success_returnsOK() {
        addToQueue("waiting:concert1", "user1", 1.0);
        setTickets("tickets:concert1", 100);
        String resp = exec("TICKET.BUY", "waiting:concert1", "tickets:concert1", "user1", "300");
        assertEquals("+OK\r\n", resp);
    }
    @Test

    public void testBuy_decrementsTicketCounter() {
        addToQueue("waiting:concert2", "user1", 1.0);
        setTickets("tickets:concert2", 5);
        exec("TICKET.BUY", "waiting:concert2", "tickets:concert2", "user1", "300");
        String resp = exec("GET", "tickets:concert2");
        assertEquals("$1\r\n4\r\n", resp);
    }
    @Test

    public void testBuy_removesUserFromWaitingRoom() {
        addToQueue("waiting:concert3", "user1", 1.0);
        setTickets("tickets:concert3", 5);
        exec("TICKET.BUY", "waiting:concert3", "tickets:concert3", "user1", "300");
        String resp = exec("ZRANK", "waiting:concert3", "user1");
        assertEquals("$-1\r\n", resp);
    }
    @Test

    public void testBuy_createsReservationKey() {
        addToQueue("waiting:concert4", "user42", 1.0);
        setTickets("tickets:concert4", 5);
        exec("TICKET.BUY", "waiting:concert4", "tickets:concert4", "user42", "300");
        // Reservation key should exist
        String resp = exec("EXISTS", "reservation:user42");
        assertEquals(":1\r\n", resp);
    }
    @Test

    public void testBuy_reservationHasTtl() {
        addToQueue("waiting:concert5", "user99", 1.0);
        setTickets("tickets:concert5", 5);
        exec("TICKET.BUY", "waiting:concert5", "tickets:concert5", "user99", "300");
        // TTL should be set (> 0)
        String resp = exec("TTL", "reservation:user99");
        assertFalse(resp.equals(":0\r\n"), "TTL should be positive");
        assertFalse(resp.equals(":-1\r\n"), "TTL should not be -1 (no expiry)");
    }

    // ── Error cases ────────────────────────────────────────────────────────
    @Test

    public void testBuy_userNotInQueue_returnsError() {
        setTickets("tickets:concert6", 10);
        // No waiting room set
        String resp = exec("TICKET.BUY", "waiting:concert6", "tickets:concert6", "stranger", "300");
        assertTrue(resp.contains("NOT_IN_QUEUE") || resp.startsWith("-ERR"), "Should return NOT_IN_QUEUE error");
    }
    @Test

    public void testBuy_soldOut_returnsError() {
        addToQueue("waiting:concert7", "user1", 1.0);
        setTickets("tickets:concert7", 0); // no tickets left
        String resp = exec("TICKET.BUY", "waiting:concert7", "tickets:concert7", "user1", "300");
        assertTrue(resp.contains("SOLD_OUT"), "Should return SOLD_OUT error");
    }
    @Test

    public void testBuy_noTicketsKey_returnsError() {
        addToQueue("waiting:concert8", "user1", 1.0);
        // No tickets key set
        String resp = exec("TICKET.BUY", "waiting:concert8", "tickets:concert8", "user1", "300");
        assertTrue(resp.startsWith("-ERR"), "Should return error");
    }
    @Test

    public void testBuy_defaultTtl_usedWhenNotProvided() {
        addToQueue("waiting:concert9", "user10", 1.0);
        setTickets("tickets:concert9", 5);
        // 4 args (no ttl arg) → should use default 300s
        String resp = exec("TICKET.BUY", "waiting:concert9", "tickets:concert9", "user10");
        assertEquals("+OK\r\n", resp);
        String ttlResp = exec("TTL", "reservation:user10");
        assertFalse(ttlResp.equals(":-1\r\n"), "TTL should be positive");
    }
}
