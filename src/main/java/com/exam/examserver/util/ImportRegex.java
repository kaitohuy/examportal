package com.exam.examserver.util;

import java.util.regex.*;

public final class ImportRegex {
    private ImportRegex(){}

    /* ===== Cho phép chèn {hl}/{/hl} ở những chỗ cần bỏ qua (HEADER/ANSWER) ===== */
    private static final String HL = "(?:\\{/?hl\\}\\s*)*";

    /* ===== CORE fragments ===== */

    /* ===== CORE fragments ===== */

    // 1) Số 1 cấp có dấu chấm (ví dụ: "1.")
    private static final String NUM_SIMPLE_DOT = "\\d{1,3}\\.";

    // 2) Số nhiều cấp (ví dụ: "1.1", "1.1.1", cho phép chấm cuối)
    private static final String NUM_HIER = "\\d{1,3}(?:\\.[1-9]?\\d){1,2}\\.?" ;
//      ↑ phần sau dấu chấm cho 1–2 chữ số; bạn có thể siết thành [1-9]\d để tránh "1.0"

    // 3) Gộp lại: "1."  HOẶC  "1.1 / 1.1.1"
    private static final String NUM_ANY = "(?:" + NUM_SIMPLE_DOT + "|" + NUM_HIER + ")";

    // 4) Toàn bộ header (nhánh chữ, 'q', và nhánh số)
    private static final String HEADER_CORE =
            "(?:" +
                    // Nhánh chữ
                    HL + "(?:question|câu\\s?hỏi|câu|bài)" + HL + "\\s*(?:số)?" + HL +
                    "\\s*\\d+(?:\\.\\d+)*" + HL + "\\s*[:.)-]?" +
                    "|" +
                    // Nhánh 'q'
                    HL + "q" + HL + "[.:\\-]?" + HL + "\\s*\\d+" + HL + "\\s*[:.)-]?" +
                    "|" +
                    // Nhánh số: cho phép {hl} bao quanh số và cả quanh dấu ngăn
                    HL + "(?:" +
                    "(?:" + HL + ")?" + NUM_ANY + "(?:" + HL + ")?" +
                    "(?:" + HL + "[\\.)\\-:]" + HL + "\\s*|\\s+)" +
                    ")" +
                    ")";

    // Header chỉ có nhánh chữ (KHÔNG có số thuần) – dùng để BREAK dòng
    private static final String HEADER_BREAK_CORE =
            "(?:"
                    + HL + "(?:question|câu\\s?hỏi|câu|bài)" + HL + "\\s*(?:số)?" + HL + "\\s*\\d+(?:\\.\\d+)*" + HL + "\\s*[:.)-]?"
                    + "|" + HL + "q" + HL + "[.:\\-]?" + HL + "\\s*\\d+" + HL + "\\s*[:.)-]?"
                    + ")";

    private static final String ANSWER_CORE =
            HL + "(?:đáp\\s*án|answer|giải\\s*thích|gợi\\s*ý)";

    // CHỈ option mới yêu cầu A–D HOA; có thể có {hl} ngay trước chữ cái
    private static final String OPT_HEAD_CORE =
            "(?:\\{hl\\}\\s*)?[A-D]\\s*[.\\)]\\s+\\S";

    /* ===== Flags ===== */
    private static final int RXF   = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final int RXFM  = RXF | Pattern.MULTILINE;     // head
    private static final int OP_MS = Pattern.MULTILINE | Pattern.DOTALL; // extract

    /* ===== Public patterns ===== */
    public static final Pattern P_SPLIT_BY_HEADER =
            Pattern.compile("(?=^\\s*" + HEADER_CORE + ")", RXFM);

    public static final Pattern P_HEADER_ANCHORED =
            Pattern.compile("^\\s*" + HEADER_CORE, RXFM);

    public static final Pattern P_OPT_EXTRACT = Pattern.compile(
            // {hl}? A–D .|)  …  (đến trước dòng mới có option tiếp theo hoặc hết block)
            "^\\s*(?:\\{hl\\}\\s*)?([A-D])\\s*[.\\)]\\s*(.+?)(?=(?:^\\s*(?:\\{hl\\}\\s*)?[A-D]\\s*[.\\)]|\\Z))",
            OP_MS
    );

    public static final Pattern P_ANSWER_LABEL =
            Pattern.compile("^\\s*(" + ANSWER_CORE + ")\\s*[:\\-]?\\s*(.+)$", RXFM);

    public static final Pattern P_IMAGE_PLACEHOLDER =
            Pattern.compile("\\{\\{image(\\d+)\\}\\}");

    // dùng khi ĐÃ biết là MC
    private static final Pattern BR_OPT =
            Pattern.compile("(?<![\\r\\n])\\s+(?=" + OPT_HEAD_CORE + ")");

    /* ===== Inline breakers ===== */
    private static final Pattern BR_HEADER =
            Pattern.compile("(?<![\\r\\n])\\s+(?=" + HEADER_BREAK_CORE + ")", RXF);
    private static final Pattern BR_ANSWER =
            Pattern.compile("(?<![\\r\\n])\\s+(?=" + ANSWER_CORE + "\\s*[:\\-])", RXF);

    /* ===== Chapter ===== */
    private static final String CHAPTER_CORE =
            HL + "(?:chương|chuong|chapter)" + HL + "\\s*(?:số)?" + HL + "\\s*(\\d+)" + HL + "\\s*[:.)-]?";

    public static final Pattern P_SPLIT_BY_CHAPTER =
            Pattern.compile("(?=^\\s*" + CHAPTER_CORE + ")", RXFM);

    public static final Pattern P_CHAPTER_ANCHORED =
            Pattern.compile("^\\s*" + CHAPTER_CORE + ".*$", RXFM);

    private static final String CHAPTER_BREAK_CORE =
            HL + "(?:chương|chuong|chapter)" + HL + "\\s*(?:số)?"+ HL + "\\s*\\d+(?:\\.\\d+)*" + HL + "\\s*[:.)-]?";

    private static final Pattern BR_CHAPTER =
            Pattern.compile("(?<![\\r\\n])\\s+(?=" + CHAPTER_BREAK_CORE + ")", RXF);

    public static final Pattern P_NUM_HEADER_LINE = Pattern.compile(
            "(?m)^\\s*(?:" +
                    // Có dấu ngăn (trong {hl} cũng được), có/không khoảng trắng sau đó
                    "(?:" + HL + ")?" + NUM_ANY + "(?:" + HL + ")?" +
                    "(?:" + HL + "[\\.)\\-:]" + HL + "\\s*)" +
                    "|" +
                    // Không có dấu ngăn: cần ít nhất 1 space sau số
                    "(?:" + HL + ")?" + NUM_ANY + "(?:" + HL + ")?" + "\\s+" +
                    ")"
    );

    public static final Pattern P_BULLET_LINE =
            Pattern.compile("^\\s*(?:[-•]\\s+|[a-dA-D][\\)\\.]\\s+|\\([a-dA-DA-D]\\)\\s+).*");

    public static String breakChapterInline(String s) {
        if (s == null) return null;
        return BR_CHAPTER.matcher(s).replaceAll("\n");
    }

    public static final Pattern P_DOC_HEADER_HINT = Pattern.compile(
            "(?is)\\b(" +
                    "CỘNG\\s+HÒA|CONG\\s+HOA|" +
                    "XÃ\\s+HỘI|XA\\s+HOI|" +
                    "ĐỘC\\s+LẬP|DOC\\s+LAP|" +
                    "BỘ\\s+GIÁO\\s+DỤC|BO\\s+GIAO\\s+DUC|" +
                    "TRƯỜNG|TRUONG|KHOA\\b|KHOÁ\\b|KHOA\\s+HỌC|" +
                    "ĐỀ\\s+THI|DE\\s+THI|MÔN\\s+HỌC|MON\\s+HOC|" +
                    "SỞ\\s+GD|SO\\s+GD|PHÒNG\\s+GD|PHONG\\s+GD" +
                    ")\\b"
    );

    private static String smartBreakNumericHeaders(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            int j = s.indexOf('\n', i);
            if (j < 0) j = n;
            String line = s.substring(i, j);
            Matcher m = P_NUM_HEADER_LINE.matcher(line);  // dùng pattern đã compile
            if (m.find()) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                out.append(line.trim());
            } else {
                out.append(line);
            }
            if (j < n) out.append('\n');
            i = j + 1;
        }
        return out.toString();
    }

    public static String breakHeaderAnswerInline(String s) {
        if (s == null) return null;
        s = BR_HEADER.matcher(s).replaceAll("\n");
        s = BR_ANSWER.matcher(s).replaceAll("\n");
        return smartBreakNumericHeaders(s);
    }

    public static Integer findChapterNumber(String s) {
        if (s == null) return null;
        Matcher m = P_CHAPTER_ANCHORED.matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return null;
    }

    public static String stripChapterHeader(String s) {
        if (s == null) return null;
        return P_CHAPTER_ANCHORED.matcher(s).replaceFirst("").trim();
    }

    // Nén {hl}
    public static String compactHighlightMarkers(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\{/hl}\\{hl\\}", "");
        s = s.replaceAll("(\\{hl\\}){2,}", "{hl}");
        s = s.replaceAll("(\\{/hl\\}){2,}", "{/hl}");
        s = s.replaceAll("\\{hl\\}\\{/hl\\}", "");
        return s;
    }

    // Chỉ break option – gọi SAU khi xác định là MC
    public static String breakOptionsInline(String s) {
        if (s == null) return null;
        return BR_OPT.matcher(s).replaceAll("\n");
    }

    // ImportRegex.java
    public static String stripHeader(String block) {
        String s = P_HEADER_ANCHORED.matcher(block).replaceFirst("").trim();
        // Ăn nốt {/hl} nếu header được bọc {hl}…{/hl}
        s = s.replaceFirst("^\\s*\\{/hl\\}\\s*", "");
        return s;
    }


    public static String stripInlineMarkers(String s) {
        if (s == null) return null;
        return s.replaceAll("\\{/?[a-z]+\\}", "");
    }
}
