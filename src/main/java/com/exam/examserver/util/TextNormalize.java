package com.exam.examserver.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class TextNormalize {
    private TextNormalize(){}

    /** Helper: giữ mapping đầu tiên nếu bị khai báo trùng */
    private static void putOnce(Map<Character,String> m, char k, String v) {
        if (m.containsKey(k)) return;
        m.put(k, v);
    }

    private static final Pattern P_SUP_RUN =
            Pattern.compile("[\\u2070-\\u209F\\u1D2C-\\u1D7F\\u02B0-\\u02FF]+");
    private static final Pattern P_SUB_RUN =
            Pattern.compile("[\\u2080-\\u209F]+");

    private static final Map<Character,String> SUP_BASE = new HashMap<>();
    private static final Map<Character,String> SUB_BASE = new HashMap<>();
    static {
        // Superscript digits & signs
        SUP_BASE.put('⁰',"0"); SUP_BASE.put('¹',"1"); SUP_BASE.put('²',"2"); SUP_BASE.put('³',"3");
        SUP_BASE.put('⁴',"4"); SUP_BASE.put('⁵',"5"); SUP_BASE.put('⁶',"6"); SUP_BASE.put('⁷',"7");
        SUP_BASE.put('⁸',"8"); SUP_BASE.put('⁹',"9");
        SUP_BASE.put('⁺',"+"); SUP_BASE.put('⁻',"-"); SUP_BASE.put('⁼',"="); SUP_BASE.put('⁽',"("); SUP_BASE.put('⁾',")");
        // Superscript lowercase (hai block: Spacing Modifier + Phonetic)
        SUP_BASE.put('ᵃ',"a"); SUP_BASE.put('ᵇ',"b"); SUP_BASE.put('ᶜ',"c"); SUP_BASE.put('ᵈ',"d"); SUP_BASE.put('ᵉ',"e");
        SUP_BASE.put('ᶠ',"f"); SUP_BASE.put('ᵍ',"g"); SUP_BASE.put('ʰ',"h"); SUP_BASE.put('ⁱ',"i"); SUP_BASE.put('ʲ',"j");
        SUP_BASE.put('ᵏ',"k"); SUP_BASE.put('ˡ',"l"); SUP_BASE.put('ᵐ',"m"); SUP_BASE.put('ⁿ',"n"); SUP_BASE.put('ᵒ',"o");
        SUP_BASE.put('ᵖ',"p"); SUP_BASE.put('ʳ',"r"); SUP_BASE.put('ˢ',"s"); SUP_BASE.put('ᵗ',"t"); SUP_BASE.put('ᵘ',"u");
        SUP_BASE.put('ᵛ',"v"); SUP_BASE.put('ʷ',"w"); SUP_BASE.put('ˣ',"x"); SUP_BASE.put('ʸ',"y"); SUP_BASE.put('ᶻ',"z");
        // Superscript uppercase (Phonetic Extensions)
        SUP_BASE.put('ᴬ',"A"); SUP_BASE.put('ᴮ',"B"); SUP_BASE.put('ᴰ',"D"); SUP_BASE.put('ᴱ',"E"); SUP_BASE.put('ᴳ',"G");
        SUP_BASE.put('ᴴ',"H"); SUP_BASE.put('ᴵ',"I"); SUP_BASE.put('ᴶ',"J"); SUP_BASE.put('ᴷ',"K"); SUP_BASE.put('ᴸ',"L");
        SUP_BASE.put('ᴹ',"M"); SUP_BASE.put('ᴺ',"N"); SUP_BASE.put('ᴼ',"O"); SUP_BASE.put('ᴾ',"P"); SUP_BASE.put('ᴿ',"R");
        SUP_BASE.put('ᵀ',"T"); SUP_BASE.put('ᵁ',"U"); SUP_BASE.put('ᵂ',"W");

        // Subscript digits & signs
        SUB_BASE.put('₀',"0"); SUB_BASE.put('₁',"1"); SUB_BASE.put('₂',"2"); SUB_BASE.put('₃',"3"); SUB_BASE.put('₄',"4");
        SUB_BASE.put('₅',"5"); SUB_BASE.put('₆',"6"); SUB_BASE.put('₇',"7"); SUB_BASE.put('₈',"8"); SUB_BASE.put('₉',"9");
        SUB_BASE.put('₊',"+"); SUB_BASE.put('₋',"-"); SUB_BASE.put('₌',"="); SUB_BASE.put('₍',"("); SUB_BASE.put('₎',")");
        // Subscript letters sẵn có trong block
        SUB_BASE.put('ₐ',"a"); SUB_BASE.put('ₑ',"e"); SUB_BASE.put('ₒ',"o"); SUB_BASE.put('ₓ',"x");
        SUB_BASE.put('ₕ',"h"); SUB_BASE.put('ₖ',"k"); SUB_BASE.put('ₗ',"l"); SUB_BASE.put('ₘ',"m");
        SUB_BASE.put('ₙ',"n"); SUB_BASE.put('ₚ',"p"); SUB_BASE.put('ₛ',"s"); SUB_BASE.put('ₜ',"t");
    }

    /** Chỉ map các ký tự PUA/Symbol đã biết → Unicode chuẩn (tránh tofu) */
    public static final Map<Character, String> PUA_REMAP = new HashMap<>();
    static {
        // toán học & ký hiệu hay gặp từ các font Symbol/Wingdings
        putOnce(PUA_REMAP,'',"=");   // PUA
        putOnce(PUA_REMAP,'',"≥");
        putOnce(PUA_REMAP,'',"≤");
        putOnce(PUA_REMAP,'',"(");
        putOnce(PUA_REMAP,'',")");
        putOnce(PUA_REMAP,'',"∀");
        putOnce(PUA_REMAP,'',"∃");
        putOnce(PUA_REMAP,'',"¬");
        putOnce(PUA_REMAP,'',"∪");
        putOnce(PUA_REMAP,'',"∩");
        putOnce(PUA_REMAP,'',"∈");
        putOnce(PUA_REMAP,'',"∉");
        putOnce(PUA_REMAP,'',"⇒");
        putOnce(PUA_REMAP,'',"⇐");
        putOnce(PUA_REMAP,'',"→");
        putOnce(PUA_REMAP,'',"↔");
        putOnce(PUA_REMAP,'',"∨");
        putOnce(PUA_REMAP,'',"∧");
        putOnce(PUA_REMAP,'',"×");
        putOnce(PUA_REMAP,'',"≡");
        putOnce(PUA_REMAP,'',"≈");
        putOnce(PUA_REMAP,'',"+");
        putOnce(PUA_REMAP,'',"−");   // minus
        putOnce(PUA_REMAP,'',"∝");
        putOnce(PUA_REMAP,'',"√");
        putOnce(PUA_REMAP,'',"∞");
        putOnce(PUA_REMAP,'',"∠");
        putOnce(PUA_REMAP,'',"∅");
        putOnce(PUA_REMAP,'',"⊆");
        putOnce(PUA_REMAP,'',"⊇");
        putOnce(PUA_REMAP,'',"⊂");
        putOnce(PUA_REMAP,'',"⊃");
        putOnce(PUA_REMAP,'',"μ");
        putOnce(PUA_REMAP,'',"π");
        putOnce(PUA_REMAP,'',"ρ");
        putOnce(PUA_REMAP,'',"σ");
        putOnce(PUA_REMAP,'',"τ");
        putOnce(PUA_REMAP,'',"φ");
        putOnce(PUA_REMAP,'',"Γ");
        putOnce(PUA_REMAP,'',"Λ");
        putOnce(PUA_REMAP,'',"Ψ");
        putOnce(PUA_REMAP,'',"Σ");
        putOnce(PUA_REMAP,'',"Θ");
        putOnce(PUA_REMAP,'',"Ω");
        putOnce(PUA_REMAP,'\uF0DB',"⇔");
        putOnce(PUA_REMAP, '\uF020', " "); //   (PUA space → space thường)
        putOnce(PUA_REMAP, '\uF028', "("); // 
        putOnce(PUA_REMAP, '\uF029', ")"); // 
        putOnce(PUA_REMAP, '\uF0C8', "∪"); //   (union)

        // một số ký hiệu chung
        putOnce(PUA_REMAP,'',"÷");
        putOnce(PUA_REMAP,'',"±");
        putOnce(PUA_REMAP,'',"✓");
        putOnce(PUA_REMAP,'',"✗");
        putOnce(PUA_REMAP,'',"′");
        putOnce(PUA_REMAP,'',"″");
        putOnce(PUA_REMAP,'',"‰");
        putOnce(PUA_REMAP,'',"‱");
        putOnce(PUA_REMAP,'',"℃");
        putOnce(PUA_REMAP,'',"℉");
        putOnce(PUA_REMAP,'\uF05B', "["); // [ từ font symbol
        putOnce(PUA_REMAP,'\uF05D', "]"); // ] từ font symbol
    }

    public static String remapPUA(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            String rep = PUA_REMAP.get(ch);
            if (rep != null) out.append(rep);
            else if (ch >= '\uE000' && ch <= '\uF8FF') out.append(' '); // fallback: thay PUA lạ bằng space
            else out.append(ch);
        }
        return out.toString();
    }

    /** Map một chuỗi superscript/subscript về "base" ASCII */
    private static String mapSuperSubRun(String run, boolean sup) {
        Map<Character,String> map = sup ? SUP_BASE : SUB_BASE;
        StringBuilder b = new StringBuilder(run.length());
        for (int i = 0; i < run.length(); i++) {
            char ch = run.charAt(i);
            String rep = map.get(ch);
            b.append(rep != null ? rep : ch); // nếu ký tự lạ, giữ nguyên
        }
        return b.toString();
    }

    /** Tìm các RUN super/sub liên tiếp, bọc thành ^{…} hoặc _{…} */
    public static String normalizeSuperSubRuns(String s) {
        if (s == null || s.isEmpty()) return s;

        // Superscript
        java.util.regex.Matcher ms = P_SUP_RUN.matcher(s);
        StringBuffer tmp = new StringBuffer();
        while (ms.find()) {
            String base = mapSuperSubRun(ms.group(), true);
            ms.appendReplacement(tmp, java.util.regex.Matcher.quoteReplacement("^{" + base + "}"));
        }
        ms.appendTail(tmp);

        // Subscript
        java.util.regex.Matcher mb = P_SUB_RUN.matcher(tmp.toString());
        StringBuffer out = new StringBuffer();
        while (mb.find()) {
            String base = mapSuperSubRun(mb.group(), false);
            mb.appendReplacement(out, java.util.regex.Matcher.quoteReplacement("_{" + base + "}"));
        }
        mb.appendTail(out);

        return out.toString();
    }

    /** Chuẩn hoá cứng: PUA→Unicode, bỏ zero-width, NBSP… và NFKC */
    public static String normalizeHard(String s) {
        if (s == null) return null;
        String t = remapPUA(s)
                // Xử lý khoảng trắng đặc biệt
                .replace('\u00A0',' ')   // No-break space
                .replace('\u2007',' ')   // Figure space
                .replace('\u202F',' ')   // Narrow no-break space
                .replace('\u2000',' ')   // En quad
                .replace('\u2001',' ')   // Em quad
                .replace('\u2002',' ')   // En space
                .replace('\u2003',' ')   // Em space
                .replace('\u2004',' ')   // Three-per-em space
                .replace('\u2005',' ')   // Four-per-em space
                .replace('\u2006',' ')   // Six-per-em space
                .replace('\u2008',' ')   // Punctuation space
                .replace('\u2009',' ')   // Thin space
                .replace('\u200A',' ')   // Hair space

                // Loại bỏ ký tự không hiển thị
                .replace("\u200B","")    // Zero width space
                .replace("\u200C","")    // Zero width non-joiner
                .replace("\u200D","")    // Zero width joiner
                .replace("\uFEFF","")    // Zero width no-break space
                .replace("\u00AD","")    // Soft hyphen
                .replace("\u0085","")    // Next line (NEL)
                .replace("\u2028","")    // Line separator
                .replace("\u2029","");   // Paragraph separator

        return Normalizer.normalize(t, Normalizer.Form.NFKC);
    }

    public static String normalizeSoftMath(String s) {
        if (s == null) return null;
        // 1) PUA → Unicode (và thay PUA lạ bằng space)
        String t = remapPUA(s);
        // 2) Giữ newline, dọn khoảng trắng nhưng KHÔNG NFKC
        t = normalizePreserveNewlines(t);
        // 3) Bỏ zero-width / BOM… (nhưng đừng NFKC)
        t = t
                .replace("\u200B","")
                .replace("\u200C","")
                .replace("\u200D","")
                .replace("\uFEFF","")
                .replace("\u00AD",""); // soft hyphen
        // 4) Gom dải super/sub Unicode → ^{…}/_{…}
        t = normalizeSuperSubRuns(t);
        return t;
    }

    public static String normalizePreserveNewlines(String s) {
        if (s == null) return null;
        s = s.replace("\r\n", "\n").replace('\r', '\n');     // chuẩn hoá xuống dòng
        s = s.replaceAll("[\\t\\x0B\\f\\u00A0]+", " ");      // gộp tab/space, KHÔNG đụng '\n'
        s = s.replaceAll(" *\n *", "\n");                    // dọn space sát newline
        s = s.replaceAll(" {2,}", " ");                      // gộp space thừa trong một dòng
        return s;
    }
}