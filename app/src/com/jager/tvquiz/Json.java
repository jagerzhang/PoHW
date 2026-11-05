package com.jager.tvquiz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器（只支持标准 JSON 的对象 / 数组 / 字符串 / 数字 / 字面量）。
 *
 * <p>APP 运行时要读 assets/syllabus.json，但又不想为一张静态表引入第三方库，
 * 也不想让纯 JVM 单测依赖 android.jar 里的 org.json。解析结果用普通
 * {@code Map / List / String / Long / Double / Boolean / null} 表示，
 * 因此本类与 Android 完全无关，可在 tests/run.sh 里直接编译执行。
 *
 * <p>故意保持严格：尾随内容、缺逗号、控制字符、非法转义、坏数字都直接抛异常，
 * 让损坏的进度表在构建/单测阶段暴露，而不是静默退化成「不限进度」。
 */
public final class Json {

    private final String text;
    private int pos;

    private Json(String text) { this.text = text; }

    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("JSON input is null");
        Json p = new Json(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != text.length()) throw p.err("trailing content");
        return v;
    }

    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException("JSON " + msg + " at offset " + pos);
    }

    private void skipWs() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
            else break;
        }
    }

    private Object value() {
        skipWs();
        if (pos >= text.length()) throw err("unexpected end");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return number();
        }
    }

    private void literal(String lit) {
        if (!text.startsWith(lit, pos)) throw err("bad literal");
        pos += lit.length();
    }

    private Map<String, Object> object() {
        pos++; // '{'
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        skipWs();
        if (pos < text.length() && text.charAt(pos) == '}') { pos++; return m; }
        while (true) {
            skipWs();
            if (pos >= text.length() || text.charAt(pos) != '"') throw err("object key expected");
            String k = string();
            skipWs();
            if (pos >= text.length() || text.charAt(pos) != ':') throw err("colon expected");
            pos++;
            m.put(k, value());
            skipWs();
            if (pos >= text.length()) throw err("unterminated object");
            char c = text.charAt(pos++);
            if (c == '}') return m;
            if (c != ',') throw err("comma expected");
        }
    }

    private List<Object> array() {
        pos++; // '['
        ArrayList<Object> a = new ArrayList<Object>();
        skipWs();
        if (pos < text.length() && text.charAt(pos) == ']') { pos++; return a; }
        while (true) {
            a.add(value());
            skipWs();
            if (pos >= text.length()) throw err("unterminated array");
            char c = text.charAt(pos++);
            if (c == ']') return a;
            if (c != ',') throw err("comma expected");
        }
    }

    private String string() {
        if (pos >= text.length() || text.charAt(pos) != '"') throw err("string expected");
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= text.length()) throw err("bad escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > text.length()) throw err("bad unicode escape");
                        String h = text.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(h, 16));
                        } catch (NumberFormatException ex) {
                            throw err("bad unicode hex");
                        }
                        pos += 4;
                        break;
                    default: throw err("unknown escape");
                }
            } else {
                if (c < 0x20) throw err("control character in string");
                sb.append(c);
            }
        }
        throw err("unterminated string");
    }

    private Object number() {
        int start = pos;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+'
                    || c == '.' || c == 'e' || c == 'E') pos++;
            else break;
        }
        if (start == pos) throw err("value expected");
        String n = text.substring(start, pos);
        try {
            if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) {
                return Long.valueOf(Long.parseLong(n));
            }
            return Double.valueOf(Double.parseDouble(n));
        } catch (NumberFormatException e) {
            throw err("bad number");
        }
    }
}
