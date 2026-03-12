package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;

/**
 * ZREMRANGEBYSCORE key min max
 *
 * Verilen score aralığındaki tüm üyeleri siler.
 * Dönüş: silinen eleman sayısı
 *
 * Örnek:
 *   ZREMRANGEBYSCORE waitQueue -inf 1700000000  → eski kayıtları temizle
 */
public class ZRemRangeByScoreCommand implements Command {

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length != 4) {
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

        Db db = Db.getInstance();
        RedisObject obj = db.getObject(key);

        if (obj == null) {
            conn.writeInteger(0);
            return;
        }
        if (!obj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        }

        long removed = obj.asZset().zremrangeByScore(min, max);
        conn.writeInteger(removed);
    }

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
