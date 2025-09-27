package com.exam.examserver.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextSim {
    private TextSim(){}

    /** Bỏ dấu Unicode accents. */
    public static String stripAccents(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    /** Chuẩn hoá tiếng Việt sang Latin ASCII: bỏ dấu + đổi đ/Đ -> d/D. */
    public static String normalizeViToLatin(String s) {
        if (s == null) return null;
        String noAccents = stripAccents(s);
        noAccents = noAccents.replace('đ', 'd').replace('Đ', 'D');
        return noAccents;
    }

    /** Bản lowercase luôn (tiện dùng trong fingerprint). */
    public static String normalizeViToLatinLower(String s) {
        if (s == null) return "";
        return normalizeViToLatin(s).toLowerCase(Locale.ROOT);
    }

    /** Gộp MC content + options để có 1 chuỗi so trùng thống nhất. */
    public static String packMultipleChoice(String content, String a, String b, String c, String d) {
        StringBuilder sb = new StringBuilder();
        if (content != null) sb.append(content).append(' ');
        if (a != null) sb.append(" a) ").append(a);
        if (b != null) sb.append(" b) ").append(b);
        if (c != null) sb.append(" c) ").append(c);
        if (d != null) sb.append(" d) ").append(d);
        return sb.toString();
    }
}
