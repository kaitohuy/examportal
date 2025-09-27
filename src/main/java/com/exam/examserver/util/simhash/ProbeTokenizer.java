package com.exam.examserver.util.simhash;

import com.exam.examserver.util.TextNormalize;
import com.exam.examserver.util.TextSim;

import java.util.ArrayList;
import java.util.List;

public final class ProbeTokenizer {
    private ProbeTokenizer(){}

    public static List<String> toTokens(String probe) {
        if (probe == null) return List.of();

        // 1) normalize + bỏ dấu TV
        String s = TextNormalize.normalizeSoftMath(probe);
        s = TextSim.normalizeViToLatinLower(s);

        // 2) giản lược latex & ký hiệu
        s = s.replaceAll("\\\\left|\\\\right", " ");
        s = s.replaceAll("\\\\times|×|·", "*");
        s = s.replaceAll("\\\\frac\\s*\\{([^}]*)}\\s*\\{([^}]*)}", "($1)/($2)");
        s = s.replaceAll("\\\\sqrt\\s*\\{([^}]*)}", "sqrt($1)");
        s = s.replaceAll("\\\\[()\\[\\]]", " ");
        s = s.replaceAll("\\\\[A-Za-z]+", " ");  // bỏ lệnh latex
        s = s.replaceAll("[\\p{Punct}&&[^*_\\-]]", " "); // giữ * _ -
        s = s.replaceAll("\\s+", " ").trim();

        // 3) word tokens
        String[] w = s.split(" ");
        List<String> tokens = new ArrayList<>(w.length * 2);
        for (String x : w) {
            if (!x.isBlank()) tokens.add(x);
        }
        // 4) bi-gram để bền với hoán vị nhỏ
        for (int i = 0; i + 1 < w.length; i++) {
            String a = w[i], b = w[i+1];
            if (!a.isBlank() && !b.isBlank()) tokens.add(a + "|" + b);
        }
        return tokens;
    }
}
