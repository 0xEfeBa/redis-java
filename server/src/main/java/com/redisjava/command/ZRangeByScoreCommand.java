package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.datastruct.SkipList;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.protocol.SafeEncoder;

import java.nio.charset.StandardCharsets;

/**
 * ZRANGEBYSCORE key min max [LIMIT offset count]
 *
 * min/max özel değerler:
 *   -inf  → Double.NEGATIVE_INFINITY
 *   +inf  → Double.POSITIVE_INFINITY
 *
 * Dönüş: üye listesi (member'lar, score yok)
 *
 * Örnekler:
 *   ZRANGEBYSCORE waitQueue -inf +inf            → tüm üyeler
 *   ZRANGEBYSCORE waitQueue -inf +inf LIMIT 0 10 → ilk 10
 *   ZRANGEBYSCORE waitQueue 1000 2000            → score aralığı
 */
public class ZRangeByScoreCommand implements Command {

    private static final byte[] OPT_LIMIT = "LIMIT".getBytes();

    @Override
    public void execute(Connection conn, RespToken[] args) {
        // ZRANGEBYSCORE key min max [LIMIT offset count]
        if (args.length < 4) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        byte[] key = args[1].getData();
        double min = parseScore(new String(args[2].getData()));
        double max = parseScore(new String(args[3].getData()));

        if (Double.isNaN(min) || Double.isNaN(max)) {
            conn.writeError("ERR min or max is not a float");
            return;
        }

        // LIMIT offset count
        long offset = 0;
        long count  = -1; // -1 = hepsi

        if (args.length >= 7) {
            byte[] limitOpt = args[4].getData();
            if (SafeEncoder.equalsIgnoreCase(limitOpt, 0, limitOpt.length, OPT_LIMIT)) {
                try {
                    offset = Long.parseLong(new String(args[5].getData()));
                    count  = Long.parseLong(new String(args[6].getData()));
                } catch (NumberFormatException e) {
                    conn.write(Responses.ERR_NOT_INTEGER);
                    return;
                }
            }
        }

        Db db = Db.getInstance();
        RedisObject obj = db.getObject(key);

        if (obj == null) {
            conn.write(Responses.EMPTY_ARRAY);
            return;
        }
        if (!obj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        SkipList.ZEntry[] entries = obj.asZset().zrangeByScore(min, max, offset, count);

        // *N\r\n
        conn.write(("*" + entries.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (SkipList.ZEntry e : entries) {
            conn.write(("$" + e.member.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            conn.write(e.member);
            conn.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** "-inf", "+inf" veya sayısal string → double */
    private static double parseScore(String s) {
        switch (s.toLowerCase()) {
            case "-inf": return Double.NEGATIVE_INFINITY;
            case "+inf": return Double.POSITIVE_INFINITY;
            default:
                try { return Double.parseDouble(s); }
                catch (NumberFormatException e) { return Double.NaN; }
        }
    }
}
