import com.jager.tvquiz.QrEncoder;

/** v1.3.1：二维码矩阵对照标准库参考向量（V3/EC-L/Byte/mask0）。
 *  参考向量随新默认端口重新生成；请勿手改 URL 而不更新 REF。 */
public class QrEncoderTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean c) {
        if (c) { pass++; System.out.println("[PASS] " + name); }
        else { fail++; System.out.println("[FAIL] " + name); }
    }

    static final String URL = "http://192.168.1.23:18730/?t=ABC123";
    static final String[] REF = {
        "11111110001000100001101111111",
        "10000010010001000011001000001",
        "10111010100100010000101011101",
        "10111010010011000000101011101",
        "10111010010011001001101011101",
        "10000010001110111001101000001",
        "11111110101010101010101111111",
        "00000000100100000111000000000",
        "11101111100010000110011000100",
        "10011101010111000110001101001",
        "11110111101110101010111110111",
        "00110000011011100100011101010",
        "10010111001100100111001101011",
        "11111100011100100000001101101",
        "01001110100001000100000000011",
        "10101100101100010110010011010",
        "00101111001010000110001000011",
        "00001001011111000010011100101",
        "10101110100110100100110001011",
        "01100000110011100110011101010",
        "10010110001100101100111110000",
        "00000000101100111110100010111",
        "11111110101001001001101011011",
        "10000010101100000101100011001",
        "10111010100010001110111111001",
        "10111010000111011011110011011",
        "10111010111110100001010011101",
        "10000010100011110111110001010",
        "11111110101100101000111001011"
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
        int[][] m3 = QrEncoder.encode("http://10.0.0.5:18730/");
        ok("Q13 其它局域网地址也能编码", m3 != null && m3.length >= 21);
        ok("Q14 null 输入返回 null", QrEncoder.encode(null) == null);

        System.out.println("VERIFY=" + (fail == 0 ? "PASS" : "FAIL")
                + " total=" + pass);
        if (fail > 0) System.exit(1);
    }
}
