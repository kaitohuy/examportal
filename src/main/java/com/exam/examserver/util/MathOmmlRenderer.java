package com.exam.examserver.util;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.officeDocument.x2006.math.CTOMath;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TeX-lite -> chèn OMML inline vào XWPFParagraph (export Word) */
public final class MathOmmlRenderer {
    private MathOmmlRenderer() {}

    // base có thể là chữ Unicode, số, hoặc dấu đóng ngoặc/đóng brace/bracket
    private static final String BASE = "([\\p{L}\\p{N}\\)\\]\\}])";

    private static final Pattern P_OVERLINE = Pattern.compile("overline\\(([^()]+|\\([^)]*\\))\\)");
    private static final Pattern P_FRAC     = Pattern.compile("frac\\((.+?),\\s*(.+?)\\)");
    private static final Pattern P_SQRT     = Pattern.compile("(?:sqrt|√)\\((.+?)\\)");
    private static final Pattern P_ROOT     = Pattern.compile("root\\((.+?)\\)\\((.+?)\\)");
    private static final Pattern P_SUBSUP   = Pattern.compile(BASE+"_\\{(.+?)\\}\\^\\{(.+?)\\}");
    private static final Pattern P_SUP      = Pattern.compile(BASE+"\\^\\{(.+?)\\}");
    private static final Pattern P_SUB      = Pattern.compile(BASE+"_\\{(.+?)\\}");
    private static final Pattern P_SUM      = Pattern.compile("sum_\\{(.+?)\\}\\^\\{(.+?)\\}\\((.+?)\\)");
    private static final Pattern P_PROD     = Pattern.compile("prod_\\{(.+?)\\}\\^\\{(.+?)\\}\\((.+?)\\)");
    private static final Pattern P_INT      = Pattern.compile("int_\\{(.+?)\\}\\^\\{(.+?)\\}\\((.+?)\\)");
    private static final Pattern P_LOGBASE  = Pattern.compile("log_\\{(.+?)\\}\\((.+?)\\)");
    private static final int OMML_PT = 13;
    private static final BigInteger OMML_SZ = BigInteger.valueOf(OMML_PT * 2);

    /** Render 1 dòng: chèn OMML cho phần match, phần còn lại là text thường (giữ style). */
    public static void emitMathAware(XWPFParagraph p, String text,
                                     Function<String, XWPFRun> runFactory) {
        if (text == null || text.isEmpty()) { runFactory.apply("").setText(""); return; }

        String s = text;
        s = replaceCasesWithOmml(p, s, runFactory);

        s = replaceWithOmml(p, s, P_OVERLINE, m -> ommlBarTop(renderPlain(m.group(1))), runFactory);
        s = replaceWithOmml(p, s, P_FRAC,     m -> ommlFrac(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);
        s = replaceWithOmml(p, s, P_ROOT,     m -> ommlRoot(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);
        s = replaceWithOmml(p, s, P_SQRT,     m -> ommlSqrt(renderPlain(m.group(1))), runFactory); // đã degHide
        s = replaceWithOmml(p, s, P_SUM,      m -> ommlNary("∑", renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_PROD,     m -> ommlNary("∏", renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_INT,      m -> ommlNary("∫", renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_LOGBASE,  m -> ommlLogWithBase(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);

        s = replaceWithOmml(p, s, P_SUBSUP,   m -> ommlSubSup(renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_SUP,      m -> ommlSup(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);
        s = replaceWithOmml(p, s, P_SUB,      m -> ommlSub(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);

        if (!s.isEmpty()) runFactory.apply(s).setText(s);
    }

    /* ---------------- balanced-cases scanner ---------------- */
    private static String replaceCasesWithOmml(
            XWPFParagraph p, String s,
            Function<String, XWPFRun> runFactory) {

        final String needle = "cases(";
        int pos = 0;
        boolean found = false;
        StringBuilder nonCase = new StringBuilder();

        while (pos < s.length()) {
            int i = indexOfCI(s, needle, pos);
            if (i < 0) { nonCase.append(s.substring(pos)); break; }
            found = true;
            nonCase.append(s, pos, i);

            int start = i + needle.length();
            int end = findMatchingParen(s, start);
            if (end < 0) { nonCase.append(s.substring(i)); break; }

            List<String> lines = splitTopLevel(s.substring(start, end), ';');
            appendOMathXml(p, ommlCases(lines));
            pos = end + 1;
        }
        return found ? nonCase.toString() : s;
    }

    private static int indexOfCI(String s, String needle, int from) {
        int n = needle.length();
        for (int i = from; i + n <= s.length(); i++)
            if (s.regionMatches(true, i, needle, 0, n)) return i;
        return -1;
    }
    private static int findMatchingParen(String s, int start) {
        int depth = 1;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }
    public static List<String> splitTopLevel(String s, char sep) {
        List<String> out = new ArrayList<>();
        int lvl = 0, last = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c=='(' || c=='[' || c=='{') lvl++;
            else if (c==')' || c==']' || c=='}') lvl = Math.max(0, lvl-1);
            else if (c==sep && lvl==0) { out.add(s.substring(last, i).trim()); last = i+1; }
        }
        out.add(s.substring(last).trim());
        return out;
    }

    /* ---------------- builders OMML (xml string) ---------------- */

    private static String ommlBarTop(String e) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:bar><m:barPr><m:pos m:val=\"top\"/></m:barPr><m:e>"+ mRun(e) +"</m:e></m:bar></m:oMath>";
    }
    private static String ommlFrac(String a, String b) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:f><m:num>"+ mRun(a) +"</m:num><m:den>"+ mRun(b) +"</m:den></m:f></m:oMath>";
    }

    // √(...) – ẩn chỉ số bậc (degHide) để nó là căn bậc 2
    private static String ommlSqrt(String x) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:e>"+ mRun(x) +"</m:e></m:rad></m:oMath>";
    }
    private static String ommlRoot(String n, String x) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:rad><m:radPr/><m:deg>"+ mRun(n) +"</m:deg><m:e>"+ mRun(x) +"</m:e></m:rad></m:oMath>";
    }
    private static String ommlSup(String base, String sup) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSup><m:e>"+ mRun(base) +"</m:e><m:sup>"+ mRun(sup) +"</m:sup></m:sSup></m:oMath>";
    }
    private static String ommlSub(String base, String sub) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSub><m:e>"+ mRun(base) +"</m:e><m:sub>"+ mRun(sub) +"</m:sub></m:sSub></m:oMath>";
    }
    private static String ommlSubSup(String base, String sub, String sup) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSubSup><m:e>"+ mRun(base) +"</m:e><m:sub>"+ mRun(sub) +"</m:sub><m:sup>"+ mRun(sup) +"</m:sup></m:sSubSup></m:oMath>";
    }
    private static String ommlNary(String chr, String lo, String hi, String e) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:nary><m:naryPr><m:chr m:val=\"" + xml(chr) + "\"/><m:limLoc m:val=\"undOvr\"/></m:naryPr>" +
                (isBlank(lo) ? "" : "<m:sub>"+ mRun(lo) +"</m:sub>") +
                (isBlank(hi) ? "" : "<m:sup>"+ mRun(hi) +"</m:sup>") +
                "<m:e>"+ mRun(e) +"</m:e></m:nary></m:oMath>";
    }
    private static String ommlLogWithBase(String base, String arg) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSub><m:e>"+ mRun("log") +"</m:e><m:sub>"+ mRun(base) +"</m:sub></m:sSub>"+ mRun("("+arg+")") +"</m:oMath>";
    }

    // Matrix 1 cột cho cases(...)
    private static String ommlCases(List<String> lines) {
        StringBuilder rows = new StringBuilder();
        for (String line : lines) {
            rows.append("<m:mr><m:e>").append(toOmmlInlineSeq(line)).append("</m:e></m:mr>");
        }
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                + "  <m:d>"
                + "    <m:dPr><m:begChr m:val=\"{\"/><m:endChr m:val=\"\"/><m:grow m:val=\"1\"/></m:dPr>"
                + "    <m:e>"
                + "      <m:m><m:mPr><m:baseJc m:val=\"centerGroup\"/></m:mPr>"
                +            rows
                + "      </m:m>"
                + "    </m:e>"
                + "  </m:d>"
                + "</m:oMath>";
    }

    // MỌI m:r đều kèm Cambria Math + size 13pt (26 half-points)
    private static String mRun(String t) {
        return "<m:r>"
                + "  <m:rPr>"
                + "    <w:rPr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "      <w:rFonts w:ascii=\"Cambria Math\" w:hAnsi=\"Cambria Math\"/>"
                + "      <w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/>"
                + "    </w:rPr>"
                + "  </m:rPr>"
                + "  <m:t xml:space=\"preserve\">" + xml(t) + "</m:t>"
                + "</m:r>";
    }

    private static String xml(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }
    private static boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }
    private static String renderPlain(String inside) { return inside == null ? "" : inside; }

    private static String replaceWithOmml(XWPFParagraph p, String s, Pattern pat,
                                          java.util.function.Function<Matcher,String> toOmml,
                                          Function<String, XWPFRun> runFactory) {
        Matcher m = pat.matcher(s);
        int last = 0;
        StringBuilder rest = new StringBuilder();
        while (m.find()) {
            if (m.start() > last) {
                String head = s.substring(last, m.start());
                if (!head.isEmpty()) runFactory.apply(head).setText(head);
            }
            String xml = toOmml.apply(m);
            appendOMathXml(p, xml);
            last = m.end();
        }
        if (last < s.length()) rest.append(s.substring(last));
        return rest.toString();
    }

    private static void appendOMathXml(XWPFParagraph p, String ommlXml) {
        try {
            CTOMath src  = (CTOMath) CTOMath.Factory.parse(ommlXml);
            CTOMath slot = p.getCTP().addNewOMath();
            slot.set(src);
            // KHÔNG động vào w:pPr/w:rPr nữa – mRun() đã set rPr cho từng m:r rồi.
        } catch (Exception e) {
            System.err.println("[OMML parse error] " + e.getMessage());
            p.createRun().setText(ommlXml); // fallback an toàn
        }
    }

    // ---------- inline TeX-lite -> chuỗi OMML (đã giữ như trước, rút gọn phần không đổi) ----------
    private static String stripOMath(String x) {
        return x.replaceFirst("^<m:oMath[^>]*>", "").replaceFirst("</m:oMath>$", "");
    }
    private static int findMatchingBrace(String s, int start) {
        int depth = 1;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }
    private static boolean isBase(char c) {
        return Character.isLetterOrDigit(c) || c==')' || c==']' || c=='}';
    }
    private static void flushPlain(StringBuilder buf, StringBuilder out) {
        if (buf.length() > 0) { out.append(mRun(buf.toString())); buf.setLength(0); }
    }

    private static String toOmmlInlineSeq(String s) {
        StringBuilder out  = new StringBuilder();
        StringBuilder text = new StringBuilder();
        int i = 0, n = s.length();

        while (i < n) {
            if (s.regionMatches(true, i, "overline(", 0, 9)) {
                int j = findMatchingParen(s, i + 9); if (j < 0) break;
                flushPlain(text, out); out.append(stripOMath(ommlBarTop(s.substring(i + 9, j)))); i = j + 1; continue;
            }
            if (s.regionMatches(true, i, "frac(", 0, 5)) {
                int j = findMatchingParen(s, i + 5); if (j < 0) break;
                flushPlain(text, out);
                List<String> ab = splitTopLevel(s.substring(i + 5, j), ',');
                String A = ab.size() > 0 ? ab.get(0) : "", B = ab.size() > 1 ? ab.get(1) : "";
                out.append(stripOMath(ommlFrac(A, B))); i = j + 1; continue;
            }
            if (s.regionMatches(true, i, "sqrt(", 0, 5)) {
                int j = findMatchingParen(s, i + 5); if (j < 0) break;
                flushPlain(text, out); out.append(stripOMath(ommlSqrt(s.substring(i + 5, j)))); i = j + 1; continue;
            }
            if (s.regionMatches(true, i, "root(", 0, 5)) {
                int j1 = findMatchingParen(s, i + 5);
                if (j1 >= 0 && j1 + 1 < n && s.charAt(j1 + 1) == '(') {
                    int j2 = findMatchingParen(s, j1 + 2);
                    if (j2 >= 0) { flushPlain(text, out);
                        out.append(stripOMath(ommlRoot(s.substring(i + 5, j1), s.substring(j1 + 2, j2))));
                        i = j2 + 1; continue;
                    }
                }
            }
            if (i + 1 < n && isBase(s.charAt(i)) && (s.charAt(i + 1) == '_' || s.charAt(i + 1) == '^')) {
                char base = s.charAt(i);
                int k = i + 1; String sub = null, sup = null;
                if (k + 1 < n && s.charAt(k) == '_' && s.charAt(k + 1) == '{') {
                    int j = findMatchingBrace(s, k + 2); if (j > 0) { sub = s.substring(k + 2, j); k = j + 1; }
                }
                if (k + 1 < n && s.charAt(k) == '^' && s.charAt(k + 1) == '{') {
                    int j = findMatchingBrace(s, k + 2); if (j > 0) { sup = s.substring(k + 2, j); k = j + 1; }
                }
                if (sub == null && sup == null && i + 2 < n && s.charAt(i + 1) == '^' && s.charAt(i + 2) == '{') {
                    int j = findMatchingBrace(s, i + 3);
                    if (j > 0) { sup = s.substring(i + 3, j); k = j + 1;
                        if (k + 1 < n && s.charAt(k) == '_' && s.charAt(k + 1) == '{') {
                            int j2 = findMatchingBrace(s, k + 2);
                            if (j2 > 0) { sub = s.substring(k + 2, j2); k = j2 + 1; }
                        }
                    }
                }
                if (sub != null || sup != null) {
                    flushPlain(text, out);
                    if (sub != null && sup != null) {
                        out.append("<m:sSubSup><m:e>").append(mRun(String.valueOf(base))).append("</m:e>")
                                .append("<m:sub>").append(mRun(sub)).append("</m:sub>")
                                .append("<m:sup>").append(mRun(sup)).append("</m:sup></m:sSubSup>");
                    } else if (sub != null) {
                        out.append("<m:sSub><m:e>").append(mRun(String.valueOf(base))).append("</m:e>")
                                .append("<m:sub>").append(mRun(sub)).append("</m:sub></m:sSub>");
                    } else {
                        out.append("<m:sSup><m:e>").append(mRun(String.valueOf(base))).append("</m:e>")
                                .append("<m:sup>").append(mRun(sup)).append("</m:sup></m:sSup>");
                    }
                    i = k; continue;
                }
            }
            text.append(s.charAt(i++));
        }
        flushPlain(text, out);
        return out.toString();
    }
}
