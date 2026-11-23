package com.jager.tvquiz;

import java.util.Random;

/**
 * issue #51(2)：内置题库正确答案偏 A（全库 31.89%、5 年级 37.41%），
 * 而 Question.fromBank() 固定保留服务端选项顺序，孩子蒙 A 的命中率明显偏高。
 *
 * <p>这里提供两件事：① 由题目内容哈希决定的确定性洗牌（同一题每次结果一致，
 * Question.key() / usedInRound 去重不会失效）；② 组内答案位置抑制，避免连续
 * 三题同一位置或一组里同位置出现三次。纯 Java，不依赖 android.*。
 */
public final class AnswerBalance {

    private AnswerBalance() {}

    /** FNV-1a 64 位内容哈希：题干 + 四个选项，作为洗牌种子。 */
    public static long seed(String text, String[] options) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, text == null ? "" : text);
        if (options != null) {
            for (String o : options) h = mix(h, o == null ? "" : o);
        }
        return h;
    }

    private static long mix(long h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        h ^= 0x9e3779b97f4a7c15L;
        h *= 0x100000001b3L;
        return h;
    }

    /** Fisher-Yates：返回 0..3 的一个排列，由种子唯一决定。 */
    public static int[] shuffleOrder(long seed) {
        int[] order = {0, 1, 2, 3};
        Random r = new Random(seed);
        for (int i = 3; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = order[i]; order[i] = order[j]; order[j] = t;
        }
        return order;
    }

    /** 选项按 order 重排后，原答案位置的新下标。 */
    public static int movedIndex(int[] order, int oldIndex) {
        if (order == null) return oldIndex;
        for (int i = 0; i < order.length; i++) if (order[i] == oldIndex) return i;
        return oldIndex;
    }

    /**
     * @param recent    最近实际采用的答案位置，末尾是最新，-1 表示空位
     * @param candidate 候选新题的答案位置
     * @return 连续两题已是该位置，或记录里该位置已出现三次时，建议重抽
     */
    public static boolean shouldRedraw(int[] recent, int candidate) {
        if (recent == null || recent.length == 0) return false;
        int run = 0, count = 0;
        boolean tail = true;
        for (int i = recent.length - 1; i >= 0; i--) {
            int v = recent[i];
            if (v < 0) continue;
            if (v == candidate) {
                count++;
                if (tail) run++;
            } else {
                tail = false;
            }
        }
        return run >= 2 || count >= 3;
    }
}
