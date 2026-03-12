package com.redisjava.datastruct;

/**
 * SipHash-2-4 implementation for hash table keys.
 */
public final class SipHash {

    private static final int C_ROUNDS = 2;
    private static final int D_ROUNDS = 4;

    private SipHash() {
    }

    public static long hash24(byte[] data, long k0, long k1) {
        long[] v = new long[4];
        v[0] = 0x736f6d6570736575L ^ k0;
        v[1] = 0x646f72616e646f6dL ^ k1;
        v[2] = 0x6c7967656e657261L ^ k0;
        v[3] = 0x7465646279746573L ^ k1;

        int end = data.length - (data.length % 8);
        int i = 0;
        while (i < end) {
            long m = ((long) data[i] & 0xff)
                    | (((long) data[i + 1] & 0xff) << 8)
                    | (((long) data[i + 2] & 0xff) << 16)
                    | (((long) data[i + 3] & 0xff) << 24)
                    | (((long) data[i + 4] & 0xff) << 32)
                    | (((long) data[i + 5] & 0xff) << 40)
                    | (((long) data[i + 6] & 0xff) << 48)
                    | (((long) data[i + 7] & 0xff) << 56);

            v[3] ^= m;
            sipRound(v, C_ROUNDS);
            v[0] ^= m;

            i += 8;
        }

        long b = ((long) data.length) << 56;
        int left = data.length & 7;
        switch (left) {
            case 7:
                b |= ((long) data[end + 6] & 0xff) << 48;
            case 6:
                b |= ((long) data[end + 5] & 0xff) << 40;
            case 5:
                b |= ((long) data[end + 4] & 0xff) << 32;
            case 4:
                b |= ((long) data[end + 3] & 0xff) << 24;
            case 3:
                b |= ((long) data[end + 2] & 0xff) << 16;
            case 2:
                b |= ((long) data[end + 1] & 0xff) << 8;
            case 1:
                b |= ((long) data[end] & 0xff);
                break;
            default:
                break;
        }

        v[3] ^= b;
        sipRound(v, C_ROUNDS);
        v[0] ^= b;

        v[2] ^= 0xff;
        sipRound(v, D_ROUNDS);

        return v[0] ^ v[1] ^ v[2] ^ v[3];
    }

    private static void sipRound(long[] v, int rounds) {
        for (int i = 0; i < rounds; i++) {
            v[0] += v[1];
            v[1] = rotl(v[1], 13);
            v[1] ^= v[0];
            v[0] = rotl(v[0], 32);

            v[2] += v[3];
            v[3] = rotl(v[3], 16);
            v[3] ^= v[2];

            v[0] += v[3];
            v[3] = rotl(v[3], 21);
            v[3] ^= v[0];

            v[2] += v[1];
            v[1] = rotl(v[1], 17);
            v[1] ^= v[2];
            v[2] = rotl(v[2], 32);
        }
    }

    private static long rotl(long x, int b) {
        return (x << b) | (x >>> (64 - b));
    }
}
