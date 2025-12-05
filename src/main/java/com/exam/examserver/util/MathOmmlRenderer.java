package com.exam.examserver.util;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.officeDocument.x2006.math.CTOMath;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LaTeX/TeX-lite -> chèn OMML inline vào XWPFParagraph (export Word) */
public final class MathOmmlRenderer {
    private MathOmmlRenderer() {
    }

    // ------- TeX-lite cũ -------
    private static final String BASE = "([\\p{L}\\p{N}\\)\\]\\}])";
    private static final Pattern P_OVERLINE = Pattern.compile("overline\\(([^()]+|\\([^)]*\\))\\)");
    private static final Pattern P_FRAC = Pattern.compile("frac\\((.+?),\\s*(.+?)\\)");
    private static final Pattern P_SQRT = Pattern.compile("(?:sqrt|√)\\((.+?)\\)");
    private static final Pattern P_ROOT = Pattern.compile("root\\((.+?)\\)\\((.+?)\\)");
    private static final Pattern P_SUBSUP = Pattern.compile(BASE + "_\\{(.+?)\\}\\^\\{(.+?)\\}");
    private static final Pattern P_SUP = Pattern.compile(BASE + "\\^\\{(.+?)\\}");
    private static final Pattern P_SUB = Pattern.compile(BASE + "_\\{(.+?)\\}");
    private static final Pattern P_SUM = Pattern.compile("sum_\\{(.+?)\\}\\^\\{(.+?)\\}\\((.+?)\\)");
    private static final Pattern P_PROD = Pattern.compile("prod_\\{(.+?)\\}\\^\\{(.+?)\\}\\((.+?)\\)");
    private static final Pattern P_INT = Pattern.compile("int_\\{(.+?)\\}\\^\\{(.+?)\\}\\((.+?)\\)");
    private static final Pattern P_LOGBASE = Pattern.compile("log_\\{(.+?)\\}\\((.+?)\\)");
    private static final Pattern L_LOGB = Pattern.compile("\\\\log_\\{(.+?)\\}\\{?\\(?(.+?)\\)?\\}?");
    private static final Pattern L_MATRIX_ENV =
            Pattern.compile("\\\\begin\\{(p|b|B|v|V)matrix\\}([\\s\\S]*?)\\\\end\\{\\1matrix\\}");
    // Gom các khối toán nhiều dòng để không bị cắt ra nhiều paragraph
    private static final Pattern P_MATH_BLOCK = Pattern.compile("(?s)"
            + "\\$\\$.*?\\$\\$"                                         // $$ ... $$
            + "|\\\\\\[.*?\\\\\\]"                                      // \[ ... \]
            + "|\\\\begin\\{(p|b|B|v|V)matrix\\}.*?\\\\end\\{\\1matrix\\}" // matrix env
            + "|\\\\begin\\{cases\\}.*?\\\\end\\{cases\\}");            // cases
    // ------- LaTeX mới -------
    private static final Pattern L_FRAC = Pattern.compile("\\\\frac\\{([^{}]*(?:\\{[^{}]*\\}[^{}]*)*)\\}\\{([^{}]*(?:\\{[^{}]*\\}[^{}]*)*)\\}");
    private static final Pattern L_SQRT = Pattern.compile("\\\\sqrt\\{(.+?)\\}");
    private static final Pattern L_ROOT = Pattern.compile("\\\\sqrt\\[(.+?)\\]\\{(.+?)\\}");
    private static final Pattern L_OVERLINE = Pattern.compile("\\\\overline\\{(.+?)\\}");
    private static final Pattern L_SUM = Pattern.compile("\\\\sum(?:_\\{(.+?)\\})?(?:\\^\\{(.+?)\\})?\\s*([^\\\\].+?)");
    private static final Pattern L_PROD = Pattern.compile("\\\\prod(?:_\\{(.+?)\\})?(?:\\^\\{(.+?)\\})?\\s*([^\\\\].+?)");
    private static final Pattern L_INT = Pattern.compile("\\\\int(?:_\\{(.+?)\\})?(?:\\^\\{(.+?)\\})?\\s*([^\\\\].+?)");
    private static final Pattern L_CASES = Pattern.compile("\\\\begin\\{cases\\}([\\s\\S]*?)\\\\end\\{cases\\}");
    // \{ ... \}
    private static final Pattern L_SET_BRACES =
            Pattern.compile("\\\\\\{\\s*([^{}]+?)\\s*\\\\\\\\}");

    // (tuỳ chọn) \lbrace ... \rbrace
    private static final Pattern L_SET_LRBRACE =
            Pattern.compile("\\\\lbrace\\s*([^{}]+?)\\s*\\\\rbrace");
    // Delimiter đơn giản: { body } hoặc ( body ), [ body ], ...
    private static String ommlDelim(String left, String right, String body) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                + "  <m:d>"
                + "    <m:dPr><m:begChr m:val=\"" + xml(left) + "\"/><m:endChr m:val=\"" + xml(right) + "\"/></m:dPr>"
                + "    <m:e>" + toOmmlInlineSeq(body) + "</m:e>"
                + "  </m:d>"
                + "</m:oMath>";
    }

    /**
     * Render 1 dòng: chèn OMML cho phần match, phần còn lại là text thường (giữ style).
     /* ---------------- TeX-lite: cases(...) scanner cũ ---------------- */
    private static String replaceCasesWithOmml(
            XWPFParagraph p, String s,
            Function<String, XWPFRun> runFactory) {

        final String needle = "cases(";
        int pos = 0;
        boolean found = false;
        StringBuilder nonCase = new StringBuilder();

        while (pos < s.length()) {
            int i = indexOfCI(s, pos);
            if (i < 0) {
                nonCase.append(s.substring(pos));
                break;
            }
            found = true;
            nonCase.append(s, pos, i);

            int start = i + needle.length();
            int end = findMatchingParen(s, start);
            if (end < 0) {
                nonCase.append(s.substring(i));
                break;
            }

            List<String> lines = splitTopLevel(s.substring(start, end), ';');
            appendOMathXml(p, ommlCases(lines));
            pos = end + 1;
        }
        return found ? nonCase.toString() : s;
    }

    private static int indexOfCI(String s, int from) {
        int n = "cases(".length();
        for (int i = from; i + n <= s.length(); i++)
            if (s.regionMatches(true, i, "cases(", 0, n)) return i;
        return -1;
    }

    private static int findMatchingParen(String s, int start) {
        int depth = 1;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    public static List<String> splitTopLevel(String s, char sep) {
        List<String> out = new ArrayList<>();
        int lvl = 0, last = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') lvl++;
            else if (c == ')' || c == ']' || c == '}') lvl = Math.max(0, lvl - 1);
            else if (c == sep && lvl == 0) {
                out.add(s.substring(last, i).trim());
                last = i + 1;
            }
        }
        out.add(s.substring(last).trim());
        return out;
    }

    /* ---------------- builders OMML (xml string) ---------------- */

    private static String ommlBarTop(String e) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:bar><m:barPr><m:pos m:val=\"top\"/></m:barPr><m:e>" + mRun(e) + "</m:e></m:bar></m:oMath>";
    }

    // a, b là LaTeX thô, ví dụ: "c_{i}", "a_{i}"
    private static String ommlFrac(String a, String b) {
        // Cho tử / mẫu đi qua parser inline để hiểu _{}, ^{}, sum, v.v.
        String num = toOmmlInlineSeq(a);
        String den = toOmmlInlineSeq(b);

        // Fallback: nếu vì lý do nào đó không parse được, quay lại mRun + renderPlain cũ
        if (num == null || num.isEmpty()) {
            num = mRun(renderPlain(a));
        }
        if (den == null || den.isEmpty()) {
            den = mRun(renderPlain(b));
        }

        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                + "<m:f>"
                + "<m:num>" + num + "</m:num>"
                + "<m:den>" + den + "</m:den>"
                + "</m:f>"
                + "</m:oMath>";
    }

    private static String ommlSqrt(String x) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:e>" + mRun(x) + "</m:e></m:rad></m:oMath>";
    }

    private static String ommlRoot(String n, String x) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:rad><m:radPr/><m:deg>" + mRun(n) + "</m:deg><m:e>" + mRun(x) + "</m:e></m:rad></m:oMath>";
    }

    private static String ommlSup(String base, String sup) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSup><m:e>" + mRun(base) + "</m:e><m:sup>" + mRun(sup) + "</m:sup></m:sSup></m:oMath>";
    }

    private static String ommlSub(String base, String sub) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSub><m:e>" + mRun(base) + "</m:e><m:sub>" + mRun(sub) + "</m:sub></m:sSub></m:oMath>";
    }

    private static String ommlSubSup(String base, String sub, String sup) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSubSup><m:e>" + mRun(base) + "</m:e><m:sub>" + mRun(sub) + "</m:sub><m:sup>" + mRun(sup) + "</m:sup></m:sSubSup></m:oMath>";
    }

    private static String ommlNary(String chr, String lo, String hi, String e) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:nary><m:naryPr><m:chr m:val=\"" + xml(chr) + "\"/><m:limLoc m:val=\"undOvr\"/></m:naryPr>" +
                (isBlank(lo) ? "" : "<m:sub>" + mRun(lo) + "</m:sub>") +
                (isBlank(hi) ? "" : "<m:sup>" + mRun(hi) + "</m:sup>") +
                "<m:e>" + mRun(e) + "</m:e></m:nary></m:oMath>";
    }

    private static String ommlLogWithBase(String base, String arg) {
        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
                "<m:sSub><m:e>" + mRun("log") + "</m:e><m:sub>" + mRun(base) + "</m:sub></m:sSub>" + mRun("(" + arg + ")") + "</m:oMath>";
    }

    // Matrix 1 cột cho cases(...)/LaTeX cases
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
                + rows
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
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '"':
                    b.append("&quot;");
                    break;
                case '\'':
                    b.append("&apos;");
                    break;
                default:
                    b.append(c);
            }
        }
        return b.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static void appendOMathXml(XWPFParagraph p, String ommlXml) {
        try {
            CTOMath src = (CTOMath) CTOMath.Factory.parse(ommlXml);
            CTOMath slot = p.getCTP().addNewOMath();
            slot.set(src);
        } catch (Exception e) {
            System.err.println("[OMML parse error] " + e.getMessage());
            p.createRun().setText(ommlXml); // fallback
        }
    }

    private static String replaceWithOmml(XWPFParagraph p, String s, Pattern pat,
                                          java.util.function.Function<Matcher, String> toOmml,
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

    // Generic matrix with delimiters: kind = p() | b[] | B{} | v|| | V‖‖
    private static String ommlMatrix(String body, char leftDelim, char rightDelim) {
        // tách dòng theo "\\\\", mỗi dòng tách cột theo "&"
        String[] rowArr = body.trim().split("\\\\\\\\");
        StringBuilder rows = new StringBuilder();
        for (String r : rowArr) {
            String[] cells = r.trim().split("&");
            StringBuilder mr = new StringBuilder("<m:mr>");
            for (String c : cells) {
                String cell = c.trim();
                String c2 = renderPlain(cell);
                mr.append("<m:e>").append(toOmmlInlineSeq(c2)).append("</m:e>");            }
            mr.append("</m:mr>");
            rows.append(mr);
        }

        String left  = String.valueOf(leftDelim);
        String right = String.valueOf(rightDelim);

        return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                + "  <m:d>"
                + "    <m:dPr><m:begChr m:val=\"" + xml(left) + "\"/><m:endChr m:val=\"" + xml(right) + "\"/><m:grow m:val=\"1\"/></m:dPr>"
                + "    <m:e>"
                + "      <m:m><m:mPr><m:baseJc m:val=\"centerGroup\"/></m:mPr>"
                +            rows
                + "      </m:m>"
                + "    </m:e>"
                + "  </m:d>"
                + "</m:oMath>";
    }

    public static List<String> chunkByMathBlocks(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        Matcher m = P_MATH_BLOCK.matcher(s);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                // phần text thường: tách theo 2 newline để ra các đoạn
                String head = s.substring(last, m.start());
                for (String para : head.split("\\R{2,}")) {
                    String t = para.trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
            // GIỮ NGUYÊN khối toán (đừng cắt theo \n)
            out.add(s.substring(m.start(), m.end()));
            last = m.end();
        }
        if (last < s.length()) {
            for (String para : s.substring(last).split("\\R{2,}")) {
                String t = para.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }
    // ========== THAY THẾ HÀM emitMathAware() ==========

    /**
     * Render 1 dòng: chèn OMML cho phần match, phần còn lại là text thường (giữ style).
     * FIX: Tách từng inline math delimiter \(...\) trước khi parse
     */
    public static void emitMathAware(XWPFParagraph p, String text,
                                     Function<String, XWPFRun> runFactory) {
        if (text == null || text.isEmpty()) {
            runFactory.apply("").setText("");
            return;
        }

        // Kiểm tra xem có cần xử lý toán không
        if (!text.matches("(?s).*(?:"
                + "\\\\begin\\{(?:p|b|B|v|V)matrix\\}"
                + "|\\\\begin\\{cases\\}"
                + "|\\\\(?:frac|sqrt|overline|sum|prod|int|log|text\\{)"
                + "|\\\\(?:\\(|\\)|\\[|\\])"
                + "|\\$\\$?"
                + "|cases\\("
                + "|frac\\("
                + "|sqrt\\("
                + "|root\\("
                + "|sum_"
                + "|prod_"
                + "|int_"
                + "|overline\\()"
                + ".*")) {

            // NEW: map các macro đơn giản (\\infty, \\alpha, ...) sang Unicode
            String plain = renderPlain(text);
            runFactory.apply(plain).setText(plain);
            return;
        }

        // BƯỚC 1: Tách các inline math blocks \(...\) riêng biệt
        List<Token> tokens = tokenizeInlineMath(text);

        for (Token token : tokens) {
            if (token.isText) {
                // Text thường
                if (!token.content.isEmpty()) {
                    runFactory.apply(token.content).setText(token.content);
                }
            } else {
                // Math block - xử lý thành OMML
                processMathBlock(p, token.content, runFactory);
            }
        }
    }

// ========== HÀM HỖ TRỢ MỚI ==========

    /**
     * Token đại diện cho 1 đoạn text hoặc 1 math block
     */
    private static class Token {
        String content;
        boolean isText;

        Token(String content, boolean isText) {
            this.content = content;
            this.isText = isText;
        }
    }

    /**
     * Tách text thành các token: text thường và math blocks
     * Hỗ trợ: \(...\), \[...\], $...$, $$...$$
     */
    private static List<Token> tokenizeInlineMath(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0, n = text.length();
        StringBuilder plainText = new StringBuilder();

        while (i < n) {
            // \( ... \)
            if (i + 1 < n && text.charAt(i) == '\\' && text.charAt(i + 1) == '(') {
                // Flush plain text trước
                if (!plainText.isEmpty()) {
                    tokens.add(new Token(plainText.toString(), true));
                    plainText.setLength(0);
                }

                // Tìm \) đóng
                int j = text.indexOf("\\)", i + 2);
                if (j > 0) {
                    String mathContent = text.substring(i + 2, j);
                    tokens.add(new Token(mathContent, false));
                    i = j + 2;
                    continue;
                }
            }

            // \[ ... \]
            if (i + 1 < n && text.charAt(i) == '\\' && text.charAt(i + 1) == '[') {
                if (!plainText.isEmpty()) {
                    tokens.add(new Token(plainText.toString(), true));
                    plainText.setLength(0);
                }

                int j = text.indexOf("\\]", i + 2);
                if (j > 0) {
                    String mathContent = text.substring(i + 2, j);
                    tokens.add(new Token(mathContent, false));
                    i = j + 2;
                    continue;
                }
            }

            // $$ ... $$
            if (i + 1 < n && text.charAt(i) == '$' && text.charAt(i + 1) == '$') {
                if (!plainText.isEmpty()) {
                    tokens.add(new Token(plainText.toString(), true));
                    plainText.setLength(0);
                }

                int j = text.indexOf("$$", i + 2);
                if (j > 0) {
                    String mathContent = text.substring(i + 2, j);
                    tokens.add(new Token(mathContent, false));
                    i = j + 2;
                    continue;
                }
            }

            // $ ... $
            if (text.charAt(i) == '$') {
                if (!plainText.isEmpty()) {
                    tokens.add(new Token(plainText.toString(), true));
                    plainText.setLength(0);
                }

                int j = text.indexOf('$', i + 1);
                if (j > 0) {
                    String mathContent = text.substring(i + 1, j);
                    tokens.add(new Token(mathContent, false));
                    i = j + 1;
                    continue;
                }
            }

            // Ký tự thường
            plainText.append(text.charAt(i));
            i++;
        }

        // Flush phần còn lại
        if (!plainText.isEmpty()) {
            tokens.add(new Token(plainText.toString(), true));
        }

        return tokens;
    }

    /**
     * Xử lý 1 math block thành OMML
     */
    private static void processMathBlock(XWPFParagraph p, String mathContent,
                                         Function<String, XWPFRun> runFactory) {
        String s = mathContent;

        // 1A) LaTeX matrix environments
        s = replaceWithOmml(p, s, L_MATRIX_ENV, m -> {
            String kind = m.group(1);
            String body = m.group(2);
            switch (kind) {
                case "p":  return ommlMatrix(body, '(', ')');
                case "b":  return ommlMatrix(body, '[', ']');
                case "B":  return ommlMatrix(body, '{', '}');
                case "v":  return ommlMatrix(body, '|', '|');
                case "V":  return ommlMatrix(body, '‖', '‖');
                default:   return ommlMatrix(body, '(', ')');
            }
        }, runFactory);

        // 1B) LaTeX cases
        s = replaceWithOmml(p, s, L_CASES, m -> {
            String body = m.group(1);
            String[] rows = body.split("\\\\\\\\");
            List<String> lines = new ArrayList<>();
            for (String ln : rows) {
                String t = ln.trim();
                if (!t.isEmpty()) lines.add(t);
            }
            return ommlCases(lines);
        }, runFactory);

        // 1C) LaTeX root, sqrt, frac, overline
        s = replaceWithOmml(p, s, L_ROOT, m -> ommlRoot(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);
        s = replaceWithOmml(p, s, L_SQRT, m -> ommlSqrt(renderPlain(m.group(1))), runFactory);
        s = replaceWithOmml(p, s, L_FRAC,
                m -> ommlFrac(m.group(1), m.group(2)), runFactory);
        s = replaceWithOmml(p, s, L_OVERLINE, m -> ommlBarTop(renderPlain(m.group(1))), runFactory);

        // 1D) LaTeX n-ary operators
        s = replaceWithOmml(p, s, L_SUM, m -> {
            String lo = nz(m.group(1)), hi = nz(m.group(2)), e = nz(m.group(3));
            return ommlNary("∑", renderPlain(lo), renderPlain(hi), renderPlain(e));
        }, runFactory);
        s = replaceWithOmml(p, s, L_PROD, m -> {
            String lo = nz(m.group(1)), hi = nz(m.group(2)), e = nz(m.group(3));
            return ommlNary("∏", renderPlain(lo), renderPlain(hi), renderPlain(e));
        }, runFactory);
        s = replaceWithOmml(p, s, L_INT, m -> {
            String lo = nz(m.group(1)), hi = nz(m.group(2)), e = nz(m.group(3));
            return ommlNary("∫", renderPlain(lo), renderPlain(hi), renderPlain(e));
        }, runFactory);

        // 1E) LaTeX delimiters
        s = replaceWithOmml(p, s, L_SET_BRACES, m -> ommlDelim("{", "}", m.group(1)), runFactory);
        s = replaceWithOmml(p, s, L_SET_LRBRACE, m -> ommlDelim("{", "}", m.group(1)), runFactory);

        // 2) TeX-lite cũ
        s = replaceCasesWithOmml(p, s, runFactory);
        s = replaceWithOmml(p, s, P_OVERLINE, m -> ommlBarTop(renderPlain(m.group(1))), runFactory);
        s = replaceWithOmml(p, s, P_FRAC,
                m -> ommlFrac(m.group(1), m.group(2)), runFactory);
        s = replaceWithOmml(p, s, P_ROOT, m -> ommlRoot(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);
        s = replaceWithOmml(p, s, P_SQRT, m -> ommlSqrt(renderPlain(m.group(1))), runFactory);
        s = replaceWithOmml(p, s, P_SUM, m -> ommlNary("∑", renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_PROD, m -> ommlNary("∏", renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_INT, m -> ommlNary("∫", renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_LOGBASE, m -> ommlLogWithBase(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);
        s = replaceWithOmml(p, s, L_LOGB, m -> ommlLogWithBase(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);

        // 3) Subscript/Superscript (pattern-based cho TeX-lite)
        s = replaceWithOmml(p, s, P_SUBSUP, m -> ommlSubSup(renderPlain(m.group(1)), renderPlain(m.group(2)), renderPlain(m.group(3))), runFactory);
        s = replaceWithOmml(p, s, P_SUP, m -> ommlSup(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);
        s = replaceWithOmml(p, s, P_SUB, m -> ommlSub(renderPlain(m.group(1)), renderPlain(m.group(2))), runFactory);

        // 4) \text{...}
        s = s.replaceAll("\\\\text\\{([^}]*)\\}", "$1");

        // 5) Phần còn lại: parse inline với subscript/superscript LaTeX-style
        if (!s.isEmpty()) {
            String ommlSeq = toOmmlInlineSeq(s);
            if (!ommlSeq.isEmpty()) {
                String wrapped = "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                        + ommlSeq + "</m:oMath>";
                appendOMathXml(p, wrapped);
            }
        }
    }

    // Thay thế phần xử lý subscript/superscript trong toOmmlInlineSeq()
// Tìm từ dòng 500 đến 550 trong MathOmmlRenderer.java

    /**
     * Chuyển đổi chuỗi TeX-lite inline thành chuỗi OMML elements (không bọc <m:oMath>)
     * FIX: Xử lý đúng subscript/superscript ngay cả khi không có base character
     */
    private static String toOmmlInlineSeq(String s) {
        StringBuilder out = new StringBuilder();
        StringBuilder text = new StringBuilder();
        int i = 0, n = s.length();

        while (i < n) {
            // --- \{ ... \} ---
            if (i + 1 < n && s.charAt(i) == '\\' && s.charAt(i + 1) == '{') {
                int j = s.indexOf("\\}", i + 2);
                if (j > 0) {
                    flushPlain(text, out);
                    String body = s.substring(i + 2, j).trim();
                    out.append(stripOMath(ommlDelim("{", "}", body)));
                    i = j + 2;
                    continue;
                }
            }

            // --- \lbrace ... \rbrace ---
            if (s.regionMatches(i, "\\lbrace", 0, 7)) {
                int start = i + 7;
                int k = s.indexOf("\\rbrace", start);
                if (k > 0) {
                    flushPlain(text, out);
                    String body = s.substring(start, k).trim();
                    out.append(stripOMath(ommlDelim("{", "}", body)));
                    i = k + 7;
                    continue;
                }
            }

            // --- overline(...) ---
            if (s.regionMatches(true, i, "overline(", 0, 9)) {
                int j = findMatchingParen(s, i + 9);
                if (j < 0) break;
                flushPlain(text, out);
                out.append(stripOMath(ommlBarTop(s.substring(i + 9, j))));
                i = j + 1;
                continue;
            }

            // --- frac(...) ---
            if (s.regionMatches(true, i, "frac(", 0, 5)) {
                int j = findMatchingParen(s, i + 5);
                if (j < 0) break;
                flushPlain(text, out);
                List<String> ab = splitTopLevel(s.substring(i + 5, j), ',');
                String A = !ab.isEmpty() ? ab.get(0) : "", B = ab.size() > 1 ? ab.get(1) : "";
                out.append(stripOMath(ommlFrac(A, B)));
                i = j + 1;
                continue;
            }

            // --- sqrt(...) ---
            if (s.regionMatches(true, i, "sqrt(", 0, 5)) {
                int j = findMatchingParen(s, i + 5);
                if (j < 0) break;
                flushPlain(text, out);
                out.append(stripOMath(ommlSqrt(s.substring(i + 5, j))));
                i = j + 1;
                continue;
            }

            // --- root(...)(...) ---
            if (s.regionMatches(true, i, "root(", 0, 5)) {
                int j1 = findMatchingParen(s, i + 5);
                if (j1 >= 0 && j1 + 1 < n && s.charAt(j1 + 1) == '(') {
                    int j2 = findMatchingParen(s, j1 + 2);
                    if (j2 >= 0) {
                        flushPlain(text, out);
                        out.append(stripOMath(ommlRoot(s.substring(i + 5, j1), s.substring(j1 + 2, j2))));
                        i = j2 + 1;
                        continue;
                    }
                }
            }

            // ===== FIX: XỬ LÝ SUBSCRIPT/SUPERSCRIPT ĐỘC LẬP =====
            // Không cần base character phía trước, xử lý trực tiếp _{...} hoặc ^{...}
            if (i + 1 < n && (s.charAt(i) == '_' || s.charAt(i) == '^')) {
                char op = s.charAt(i);
                if (s.charAt(i + 1) == '{') {
                    int j = findMatchingBrace(s, i + 2);
                    if (j > 0) {
                        flushPlain(text, out);
                        String content = s.substring(i + 2, j).trim();
                        String contentOmml = toOmmlInlineSeq(content);

                        // Tạo element rỗng làm base (placeholder)
                        if (op == '_') {
                            out.append("<m:sSub><m:e>").append(mRun(""))
                                    .append("</m:e><m:sub>").append(contentOmml)
                                    .append("</m:sub></m:sSub>");
                        } else {
                            out.append("<m:sSup><m:e>").append(mRun(""))
                                    .append("</m:e><m:sup>").append(contentOmml)
                                    .append("</m:sup></m:sSup>");
                        }
                        i = j + 1;
                        continue;
                    }
                }
            }

            // --- SUBSCRIPT/SUPERSCRIPT có base character: x_{...} x^{...} ---
            if (i + 1 < n && isBase(s.charAt(i)) && (s.charAt(i + 1) == '_' || s.charAt(i + 1) == '^')) {
                char base = s.charAt(i);
                int k = i + 1;
                String sub = null, sup = null;

                // Parse tất cả _ và ^ theo thứ tự xuất hiện
                boolean foundScript = false;
                while (k + 1 < n) {
                    if (s.charAt(k) == '_' && s.charAt(k + 1) == '{') {
                        int j = findMatchingBrace(s, k + 2);
                        if (j > 0) {
                            sub = s.substring(k + 2, j).trim();
                            k = j + 1;
                            foundScript = true;
                            continue;
                        }
                    }
                    if (s.charAt(k) == '^' && s.charAt(k + 1) == '{') {
                        int j = findMatchingBrace(s, k + 2);
                        if (j > 0) {
                            sup = s.substring(k + 2, j).trim();
                            k = j + 1;
                            foundScript = true;
                            continue;
                        }
                    }
                    break;
                }

                if (foundScript && (sub != null || sup != null)) {
                    flushPlain(text, out);
                    if (sub != null && sup != null) {
                        out.append("<m:sSubSup><m:e>").append(mRun(String.valueOf(base))).append("</m:e>")
                                .append("<m:sub>").append(toOmmlInlineSeq(sub)).append("</m:sub>")
                                .append("<m:sup>").append(toOmmlInlineSeq(sup)).append("</m:sup></m:sSubSup>");
                    } else if (sub != null) {
                        out.append("<m:sSub><m:e>").append(mRun(String.valueOf(base))).append("</m:e>")
                                .append("<m:sub>").append(toOmmlInlineSeq(sub)).append("</m:sub></m:sSub>");
                    } else {
                        out.append("<m:sSup><m:e>").append(mRun(String.valueOf(base))).append("</m:e>")
                                .append("<m:sup>").append(toOmmlInlineSeq(sup)).append("</m:sup></m:sSup>");
                    }
                    i = k;
                    continue;
                }
            }

            // Ký tự thường
            text.append(s.charAt(i++));
        }

        flushPlain(text, out);
        return out.toString();
    }

// ========== CÁC HÀM HỖ TRỢ (KHÔNG THAY ĐỔI) ==========

    private static int findMatchingBrace(String s, int start) {
        if (start >= s.length()) return -1;
        int depth = 1;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isBase(char c) {
        return Character.isLetterOrDigit(c)
                || c == ')'
                || c == ']'
                || c == '}';
    }

    private static void flushPlain(StringBuilder buf, StringBuilder out) {
        if (!buf.isEmpty()) {
            out.append(mRun(renderPlain(buf.toString())));
            buf.setLength(0);
        }
    }

    private static String stripOMath(String x) {
        return x.replaceFirst("^<m:oMath[^>]*>", "")
                .replaceFirst("</m:oMath>$", "");
    }

    private static String renderPlain(String inside) {
        String t = inside == null ? "" : inside;
        t = t.replace("\\infty", "∞")
                .replace("\\times", "×")
                .replace("\\cdot", "·")
                .replace("\\leq", "≤")
                .replace("\\geq", "≥")
                .replace("\\neq", "≠")
                .replace("\\pm", "±")
                .replace("\\mp", "∓")
                .replace("\\approx", "≈")
                .replace("\\equiv", "≡")
                .replace("\\alpha", "α")
                .replace("\\beta", "β")
                .replace("\\gamma", "γ")
                .replace("\\delta", "δ")
                .replace("\\theta", "θ")
                .replace("\\pi", "π")
                .replace("\\sigma", "σ")
                .replace("\\omega", "ω")
                .replace("\\in", "∈")
                .replace("\\subset", "⊂")
                .replace("\\cup", "∪")
                .replace("\\cap", "∩")
                .replace("\\rightarrow", "→")
                .replace("\\Rightarrow", "⇒");
        return t;
    }
}