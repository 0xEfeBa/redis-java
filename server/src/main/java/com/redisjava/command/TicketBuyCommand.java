package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.datastruct.SkipList;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * TICKET.BUY supports two compatible signatures:
 *
 * 1) Stock mode (legacy):
 *    TICKET.BUY stockKey quantity [userId maxPerUser]
 *
 * 2) Queue mode (waiting-room flow):
 *    TICKET.BUY waitingKey ticketsKey userId [reservationTtlSec]
 *
 * Queue mode semantics:
 * - user waitingKey zset içinde olmalı
 * - ticketsKey integer değeri 1 azaltılır
 * - kullanıcı waitingKey'den çıkarılır
 * - reservation:userId key'i ttl ile yazılır
 * - başarı cevabı: +OK
 */
public class TicketBuyCommand implements Command {

    private static final String USER_TICKET_PREFIX = "usertickets:";
    private static final String RESERVATION_PREFIX = "reservation:";
    private static final long DEFAULT_RESERVATION_TTL_SEC = 300L;

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 3) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        // If arg2 is numeric => stock mode, otherwise queue mode.
        String arg2 = new String(args[2].getData());
        if (isLong(arg2)) {
            executeStockMode(conn, args);
        } else {
            executeQueueMode(conn, args);
        }
    }

    private void executeQueueMode(Connection conn, RespToken[] args) {
        // TICKET.BUY waitingKey ticketsKey userId [reservationTtlSec]
        if (args.length != 4 && args.length != 5) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] waitingKey = args[1].getData();
        byte[] ticketsKey = args[2].getData();
        byte[] userId = args[3].getData();

        long ttlSec = DEFAULT_RESERVATION_TTL_SEC;
        if (args.length == 5) {
            try {
                ttlSec = Long.parseLong(new String(args[4].getData()));
            } catch (NumberFormatException e) {
                conn.write(Responses.ERR_NOT_INTEGER);
                return;
            }
            if (ttlSec <= 0) {
                conn.write(Responses.ERR_NOT_INTEGER);
                return;
            }
        }

        Db db = Db.getInstance();

        // 1) Must be present in waiting room zset
        RedisObject waitingObj = db.getObject(waitingKey);
        if (waitingObj == null) {
            conn.writeError("ERR NOT_IN_QUEUE");
            return;
        }
        if (!waitingObj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        SkipList waiting = waitingObj.asZset();
        if (waiting.zrank(userId) == -1) {
            conn.writeError("ERR NOT_IN_QUEUE");
            return;
        }

        // 2) Read/decrement ticket stock
        byte[] ticketBytes = db.get(ticketsKey);
        if (ticketBytes == null) {
            conn.writeError("ERR TICKETS_KEY_MISSING");
            return;
        }

        long stock;
        try {
            stock = Long.parseLong(new String(ticketBytes));
        } catch (NumberFormatException e) {
            conn.write(Responses.ERR_NOT_INTEGER);
            return;
        }

        if (stock <= 0) {
            conn.writeError("ERR SOLD_OUT");
            return;
        }

        db.set(ticketsKey, String.valueOf(stock - 1).getBytes());

        // 3) Remove user from waiting room
        waiting.zrem(userId);
        if (waiting.zcard() == 0) {
            db.del(waitingKey);
        }

        // 4) Create reservation key with TTL
        byte[] reservationKey = (RESERVATION_PREFIX + new String(userId)).getBytes();
        db.set(reservationKey, "1".getBytes());
        long expireAtMs = System.currentTimeMillis() + (ttlSec * 1000L);
        db.expireAt(reservationKey, expireAtMs);

        conn.write(Responses.OK);
    }

    private void executeStockMode(Connection conn, RespToken[] args) {
        // TICKET.BUY stockKey quantity [userId maxPerUser]
        byte[] stockKey = args[1].getData();
        long   quantity;
        try {
            quantity = Long.parseLong(new String(args[2].getData()));
        } catch (NumberFormatException e) {
            conn.write(Responses.ERR_NOT_INTEGER);
            return;
        }

        // Opsiyonel: userId ve maxPerUser
        byte[] userId     = args.length >= 4 ? args[3].getData() : null;
        long   maxPerUser = -1;
        if (args.length >= 5) {
            try {
                maxPerUser = Long.parseLong(new String(args[4].getData()));
            } catch (NumberFormatException e) {
                conn.write(Responses.ERR_NOT_INTEGER);
                return;
            }
        }

        Db db = Db.getInstance();

        // 1. Stok kontrolu
        byte[] stockBytes = db.get(stockKey);
        if (stockBytes == null) {
            conn.writeError("ERR SOLD_OUT");
            return;
        }

        long stock;
        try {
            stock = Long.parseLong(new String(stockBytes));
        } catch (NumberFormatException e) {
            conn.write(Responses.ERR_NOT_INTEGER);
            return;
        }

        if (stock < quantity) {
            conn.writeError("ERR SOLD_OUT");
            return;
        }

        // 2. Per-user limit kontrolu
        if (userId != null && maxPerUser > 0) {
            String userKey    = USER_TICKET_PREFIX + new String(userId);
            byte[] userBought = db.get(userKey.getBytes());
            long   bought     = userBought == null ? 0L
                    : Long.parseLong(new String(userBought));

            if (bought + quantity > maxPerUser) {
                conn.writeError("ERR LIMIT");
                return;
            }

            db.set(userKey.getBytes(), String.valueOf(bought + quantity).getBytes());
        }

        // 3. Stogu azalt, kalan stogu don
        long newStock = stock - quantity;
        db.set(stockKey, String.valueOf(newStock).getBytes());
        conn.writeInteger(newStock);
    }

    private static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
