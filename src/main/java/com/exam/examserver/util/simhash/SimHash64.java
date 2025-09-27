package com.exam.examserver.util.simhash;

public final class SimHash64 {
    private SimHash64(){}

    /** FNV-1a 64-bit */
    private static long h64(String s) {
        long x = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            x ^= (byte) s.charAt(i);
            x *= 0x100000001b3L;
        }
        return x;
    }

    public static long compute(Iterable<String> tokens) {
        int[] v = new int[64];
        for (String t : tokens) {
            if (t == null || t.isBlank()) continue;
            long h = h64(t);
            for (int i = 0; i < 64; i++) {
                v[i] += ((h >>> i) & 1L) == 1L ? 1 : -1;
            }
        }
        long out = 0L;
        for (int i = 0; i < 64; i++) if (v[i] > 0) out |= (1L << i);
        return out;
    }

    public static int hamming(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** bandIndex: 0..3 (MSB → LSB), mỗi band 16 bit */
    public static int band(long h, int bandIndex) {
        int shift = (3 - bandIndex) * 16;
        return (int)((h >>> shift) & 0xFFFFL);
    }
}
