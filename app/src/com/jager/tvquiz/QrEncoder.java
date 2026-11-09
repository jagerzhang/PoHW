package com.jager.tvquiz;

/**
 * v1.3.0 未发布：零第三方依赖的二维码编码器，用于电视上显示局域网管理地址。
 *
 * <p>只实现 PoHW 需要的最小子集：QR Model 2、Version 1–4、纠错级别 L、
 * 字节模式、mask 0。V1–V4 在 EC-L 下都是单 RS 块，因此不需要多块交织，
 * 也不需要 8 种掩模评分和版本信息块（V7+ 才有），实现量约 200 行。
 *
 * <p>纯 Java 8、零 Android 依赖，输出 row-major 的 0/1 矩阵，
 * 由 QrEncoderTest 对照标准库生成的 29x29 参考向量逐位校验。
 */
public final class QrEncoder {

    private static final int[] DATA_CW = {0, 19, 34, 55, 80};
    private static final int[] EC_CW = {0, 7, 10, 15, 20};
    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    private QrEncoder() { }

    private static int mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    /** 按 UTF-8 字节数选最小可容纳的 Version（1–4），放不下返回 -1。 */
    public static int versionFor(String text) {
        if (text == null || text.length() == 0) return -1;
        int n = text.getBytes(java.nio.charset.Charset.forName("UTF-8")).length;
        for (int v = 1; v <= 4; v++) {
            if (n + 2 <= DATA_CW[v]) return v;
        }
        return -1;
    }

    /** 生成 row-major 的 0/1 矩阵（1=黑）。text 过长或为空时返回 null。 */
    public static int[][] encode(String text) {
        if (text == null || text.length() == 0) return null;
        int version = versionFor(text);
        if (version < 0) return null;
        byte[] payload = text.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        int size = 17 + 4 * version;
        int dataCw = DATA_CW[version];
        int ecCw = EC_CW[version];

        BitBuffer bb = new BitBuffer();
        bb.append(0x4, 4);                 // byte mode
        bb.append(payload.length, 8);      // character count, 8 bits for V1-9
        for (byte b : payload) bb.append(b & 0xFF, 8);
        int capBits = dataCw * 8;
        bb.append(0, Math.min(4, Math.max(0, capBits - bb.bitLength())));
        while (bb.bitLength() % 8 != 0) bb.append(0, 1);
        byte[] raw = bb.toBytes();
        int[] data = new int[dataCw];
        for (int i = 0; i < dataCw; i++) {
            data[i] = i < raw.length ? (raw[i] & 0xFF)
                    : ((i - raw.length) % 2 == 0 ? 0xEC : 0x11);
        }
        int[] ecc = reedSolomon(data, ecCw);
        int[] code = new int[dataCw + ecCw];
        System.arraycopy(data, 0, code, 0, dataCw);
        System.arraycopy(ecc, 0, code, dataCw, ecCw);

        int[][] m = new int[size][size];
        boolean[][] reserved = new boolean[size][size];
        drawFinders(m, reserved, size);
        drawTiming(m, reserved, size);
        if (version >= 2) drawAlignment(m, reserved, size);
        reserveFormat(m, reserved, size);
        drawFormat(m, size, 8);           // EC-L(01) + mask(000)
        drawCodewords(m, reserved, size, code);
        m[size - 8][8] = 1;              // dark module
        return m;
    }

    private static void set(int[][] m, boolean[][] r, int x, int y, int v) {
        m[y][x] = v;
        r[y][x] = true;
    }

    private static void drawFinders(int[][] m, boolean[][] r, int size) {
        drawFinder(m, r, 0, 0, size);
        drawFinder(m, r, size - 7, 0, size);
        drawFinder(m, r, 0, size - 7, size);
    }

    private static void drawFinder(int[][] m, boolean[][] r,
                                   int ox, int oy, int size) {
        for (int dy = -1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 7; dx++) {
                int x = ox + dx, y = oy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                boolean dark = (dx >= 0 && dx <= 6 && (dy == 0 || dy == 6))
                        || (dy >= 0 && dy <= 6 && (dx == 0 || dx == 6))
                        || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4);
                set(m, r, x, y, dark ? 1 : 0);
            }
        }
    }

    private static void drawTiming(int[][] m, boolean[][] r, int size) {
        for (int i = 0; i < size; i++) {
            if (!r[6][i]) set(m, r, i, 6, (i % 2 == 0) ? 1 : 0);
            if (!r[i][6]) set(m, r, 6, i, (i % 2 == 0) ? 1 : 0);
        }
    }

    private static void drawAlignment(int[][] m, boolean[][] r, int size) {
        int cx = size - 7, cy = size - 7;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean dark = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                set(m, r, cx + dx, cy + dy, dark ? 1 : 0);
            }
        }
    }

    private static void reserveFormat(int[][] m, boolean[][] r, int size) {
        for (int i = 0; i <= 8; i++) {
            if (i != 6) { r[8][i] = true; r[i][8] = true; }
        }
        for (int i = size - 8; i < size; i++) {
            r[8][i] = true;
            r[i][8] = true;
        }
    }

    private static void drawFormat(int[][] m, int size, int data5) {
        int rem = data5 << 10;
        for (int i = 14; i >= 10; i--) {
            if (((rem >> i) & 1) != 0) rem ^= 0x537 << (i - 10);
        }
        int bits = ((data5 << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) m[i][8] = bit(bits, i);
        m[7][8] = bit(bits, 6);
        m[8][8] = bit(bits, 7);
        m[8][7] = bit(bits, 8);
        for (int i = 9; i < 15; i++) m[8][14 - i] = bit(bits, i);
        for (int i = 0; i < 8; i++) m[8][size - 1 - i] = bit(bits, i);
        for (int i = 8; i < 15; i++) m[size - 15 + i][8] = bit(bits, i);
    }

    private static int bit(int v, int i) { return (v >> i) & 1; }

    private static void drawCodewords(int[][] m, boolean[][] r,
                                      int size, int[] code) {
        int idx = 0;
        int totalBits = code.length * 8;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            boolean upward = ((right + 1) & 2) == 0;
            for (int vert = 0; vert < size; vert++) {
                int y = upward ? size - 1 - vert : vert;
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    if (r[y][x]) continue;
                    int v = 0;
                    if (idx < totalBits) {
                        v = (code[idx >> 3] >> (7 - (idx & 7))) & 1;
                        idx++;
                    }
                    if ((x + y) % 2 == 0) v ^= 1;   // mask 0
                    m[y][x] = v;
                }
            }
        }
    }

    private static int[] reedSolomon(int[] data, int ecLen) {
        int[] gen = new int[]{1};
        for (int i = 0; i < ecLen; i++) {
            int[] next = new int[gen.length + 1];
            for (int j = 0; j < gen.length; j++) {
                next[j] ^= gen[j];
                next[j + 1] ^= mul(gen[j], EXP[i]);
            }
            gen = next;
        }
        int[] ecc = new int[ecLen];
        for (int b : data) {
            int factor = b ^ ecc[0];
            System.arraycopy(ecc, 1, ecc, 0, ecLen - 1);
            ecc[ecLen - 1] = 0;
            for (int j = 0; j < ecLen; j++) {
                ecc[j] ^= mul(gen[j + 1], factor);
            }
        }
        return ecc;
    }

    private static final class BitBuffer {
        private final java.io.ByteArrayOutputStream out =
                new java.io.ByteArrayOutputStream();
        private int cur = 0, used = 0;

        void append(int val, int len) {
            for (int i = len - 1; i >= 0; i--) {
                cur = (cur << 1) | ((val >> i) & 1);
                used++;
                if (used == 8) { out.write(cur); cur = 0; used = 0; }
            }
        }

        int bitLength() { return out.size() * 8 + used; }

        byte[] toBytes() { return out.toByteArray(); }
    }
}
