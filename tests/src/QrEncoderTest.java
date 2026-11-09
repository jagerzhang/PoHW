import com.jager.tvquiz.QrEncoder;

/** v1.3.0：二维码矩阵对照标准库参考向量（V3/EC-L/Byte/mask0）。 */
public class QrEncoderTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    static final String URL = "http://192.168.1.23:8099/?t=ABC123";
    static final String[] REF = {
        "11111110010011001101101111111",
        "10000010001110111101001000001",
        "10111010111011101000101011101",
        "10111010011101110001101011101",
        "10111010001000100101101011101",
        "10000010010001000101001000001",
        "11111110101010101010101111111",
        "00000000111011101111000000000",
        "11101111101100111111011000100",
        "10011000001100110000001101001",
        "01100110110001000100111110111",
        "00100000100100000110011101010",
        "10100010100010000111001101011",
        "01011001000111000010001101101",
        "10001011110110101100000000011",
        "10000001011011100110010011010",
        "11011010110100100100001000011",
        "01101000110100100110011100101",
        "10000110010001000000110001011",
        "01011101111100010100011101010",
        "10111110011010000110111110000",
        "00000000100111000010100010111",
        "11111110111110100001101011011",
        "10000010100011100111100011011",
        "10111010110100101000111111001",
        "10111010000100111101110011011",
        "10111010101001001011010011101",
        "10000010110100000111110001010",
        "11111110111010001000111001011"
    };

    public static void main(String[] args) {
        ok("Q01 选中 Version 3", QrEncoder.versionFor(URL) == 3);
        ok("Q02 短文本选中 Version 1", QrEncoder.versionFor("A") == 1);
        ok("Q03 空文本返回 -1", QrEncoder.versionFor("") == -1);
        ok("Q04 超长文本返回 -1",
                QrEncoder.versionFor(new String(new char[200]).replace('\0', 'x')) == -1);
        int[][] m = QrEncoder.encode(URL);
        ok("Q05 矩阵非空", m != null);
        ok("Q06 矩阵 29x29", m != null && m.length == 29 && m[0].length == 29);
        int bad = 0;
        if (m != null) {
            for (int y = 0; y < 29; y++) {
                StringBuilder b = new StringBuilder();
                for (int x = 0; x < 29; x++) b.append(m[y][x] == 1 ? '1' : '0');
                if (!REF[y].equals(b.toString())) bad++;
            }
        }
        ok("Q07 29 行与标准库参考矩阵逐位一致", bad == 0);
        ok("Q08 左上角定位图案", m != null && m[0][0] == 1 && m[3][3] == 1);
        ok("Q09 右上定位图案", m != null && m[0][28] == 1);
        ok("Q10 左下定位图案", m != null && m[28][0] == 1);
        ok("Q11 暗模块固定为黑", m != null && m[21][8] == 1);
        int[][] m2 = QrEncoder.encode(URL);
        ok("Q12 编码可重复", m2 != null && m != null
                && java.util.Arrays.deepEquals(m, m2));
        int[][] m3 = QrEncoder.encode("http://10.0.0.5:8099/");
        ok("Q13 其它局域网地址也能编码", m3 != null && m3.length >= 21);
        ok("Q14 null 输入返回 null", QrEncoder.encode(null) == null);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL")
                + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
