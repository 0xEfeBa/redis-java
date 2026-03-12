package com.redisjava.command;

import com.redisjava.datastruct.Db;
import com.redisjava.datastruct.RedisObject;
import com.redisjava.datastruct.SkipList;
import com.redisjava.memory.EvictionManager;
import com.redisjava.network.Connection;
import com.redisjava.protocol.RespToken;
import com.redisjava.protocol.SafeEncoder;

/**
 * ZADD key [NX|XX] score member [score member …]
 *
 * NX — sadece member yoksa ekle (var olanı güncelleme)
 * XX — sadece member varsa güncelle (yeni ekleme)
 * (flag yoksa) — ekle ya da güncelle (Redis varsayılanı)
 *
 * Dönüş: eklenen (yeni) eleman sayısı
 */
public class ZAddCommand implements Command {

    private static final byte[] OPT_NX = "NX".getBytes();
    private static final byte[] OPT_XX = "XX".getBytes();

    @Override
    public void execute(Connection conn, RespToken[] args) {
        if (args.length < 4) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        EvictionManager em = EvictionManager.getInstance();
        if (em != null && !em.ensureMemory()) {
            conn.write(Responses.OOM_ERROR);
            return;
        }

        Db db   = Db.getInstance();
        byte[] key = args[1].getData();

        // ── Flag tespiti (NX / XX) ─────────────────────────────────────────
        int startIdx = 2;  // score'ların başladığı index
        boolean nx = false;
        boolean xx = false;

        byte[] maybeFlag = args[2].getData();
        if (SafeEncoder.equalsIgnoreCase(maybeFlag, 0, maybeFlag.length, OPT_NX)) {
            nx = true; startIdx = 3;
        } else if (SafeEncoder.equalsIgnoreCase(maybeFlag, 0, maybeFlag.length, OPT_XX)) {
            xx = true; startIdx = 3;
        }

        // score+member çiftleri için arg sayısı kontrolü
        int pairArgs = args.length - startIdx;
        if (pairArgs < 2 || pairArgs % 2 != 0) {
            conn.write(Responses.ERR_WRONG_ARGS);
            return;
        }

        // ── ZSet al ya da oluştur ──────────────────────────────────────────
        RedisObject obj = db.getObject(key);
        SkipList zset;

        if (obj == null) {
            if (xx) {
                // XX: key yoksa hiç ekleme
                conn.writeInteger(0);
                return;
            }
            zset = new SkipList();
            obj  = RedisObject.zset(zset);
        } else if (!obj.isZset()) {
            conn.write(Responses.WRONG_TYPE_ERROR);
            return;
        } else {
            zset = obj.asZset();
        }

        // ── Ekle / güncelle ────────────────────────────────────────────────
        int added = 0;
        for (int i = startIdx; i < args.length; i += 2) {
            double score;
            try {
                score = Double.parseDouble(new String(args[i].getData()));
            } catch (NumberFormatException e) {
                conn.write(Responses.ERR_NOT_INTEGER);
                return;
            }
            byte[] member = args[i + 1].getData();

            if (nx) {
                added += zset.zaddNx(score, member);
            } else if (xx) {
                added += zset.zaddXx(score, member);
            } else {
                added += zset.zadd(score, member);
            }
        }

        db.set(key, obj);
        conn.writeInteger(added);
    }
}
