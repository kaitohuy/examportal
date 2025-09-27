package com.exam.examserver.util.simhash;

import java.util.*;
import java.util.regex.Pattern;

/** TF-IDF cosine rất gọn cho 2 văn bản (shingle 3-5 gram). Dùng khi Hamming ở vùng 4-6. */
public final class TfidfCosine {
    private TfidfCosine(){}

    private static final Pattern WS = Pattern.compile("\\s+");

    private static List<String> shingles(String s) {
        if (s == null) return List.of();
        s = s.replaceAll("\\s+", " ").trim();
        String[] w = WS.split(s);
        List<String> r = new ArrayList<>();
        int[] ks = {3,4,5};
        for (int k : ks) {
            for (int i = 0; i + k <= w.length; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < k; j++) {
                    if (j>0) sb.append(' ');
                    sb.append(w[i+j]);
                }
                r.add(sb.toString());
            }
        }
        return r;
    }

    public static double cosine(String a, String b) {
        List<String> A = shingles(a);
        List<String> B = shingles(b);
        if (A.isEmpty() || B.isEmpty()) return 0.0;

        Map<String,Integer> df = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (String t : new HashSet<>(A)) { df.put(t, df.getOrDefault(t,0)+1); }
        seen.clear();
        for (String t : new HashSet<>(B)) { df.put(t, df.getOrDefault(t,0)+1); }

        Map<String,Integer> tfA = new HashMap<>();
        Map<String,Integer> tfB = new HashMap<>();
        for (String t : A) tfA.put(t, tfA.getOrDefault(t,0)+1);
        for (String t : B) tfB.put(t, tfB.getOrDefault(t,0)+1);

        double dot = 0, nA = 0, nB = 0;
        for (Map.Entry<String,Integer> e : tfA.entrySet()) {
            String t = e.getKey();
            double idf = Math.log(1.0 + 2.0 / (df.getOrDefault(t,1)));
            double wA = e.getValue() * idf;
            nA += wA*wA;
            double wB = tfB.getOrDefault(t,0) * idf;
            dot += wA*wB;
        }
        for (Map.Entry<String,Integer> e : tfB.entrySet()) {
            String t = e.getKey();
            if (tfA.containsKey(t)) continue;
            double idf = Math.log(1.0 + 2.0 / (df.getOrDefault(t,1)));
            double wB = e.getValue() * idf;
            nB += wB*wB;
        }
        // nB còn thiếu phần t đã tính chung; bổ sung:
        for (Map.Entry<String,Integer> e : tfA.entrySet()) {
            String t = e.getKey();
            double idf = Math.log(1.0 + 2.0 / (df.getOrDefault(t,1)));
            double wB = tfB.getOrDefault(t,0) * idf;
            nB += wB*wB;
        }
        nA = Math.sqrt(nA);
        nB = Math.sqrt(nB);
        if (nA == 0 || nB == 0) return 0.0;
        return dot / (nA * nB);
    }
}
