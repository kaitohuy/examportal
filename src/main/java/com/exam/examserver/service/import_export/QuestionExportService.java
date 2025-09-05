package com.exam.examserver.service.import_export;

import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.service.QuestionService;
import com.exam.examserver.util.TextNormalize;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.layout.font.FontSet;
import com.itextpdf.layout.properties.Property;

import org.scilab.forge.jlatexmath.TeXFormula;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.exam.examserver.util.MathOmmlRenderer.splitTopLevel;
import static com.exam.examserver.util.MathOmmlRenderer.emitMathAware;

@Service
public class QuestionExportService {

    private final QuestionService questionService;

    private static final String FONT_TEXT = "src/main/resources/fonts/NotoSans-Regular.ttf";
    private static final String FONT_MATH = "src/main/resources/fonts/NotoSansMath-Regular.ttf";
    /** Header data cho mẫu ôn tập */
    public static record PracticeHeader(
            String bankTitle,    // "NGÂN HÀNG CÂU HỎI THI TỰ LUẬN" | "… TRẮC NGHIỆM"
            String subjectName,  // Tên học phần
            String subjectCode,  // Mã học phần
            String program,      // Ngành đào tạo (nullable)
            String level         // Trình độ đào tạo
    ) {}

    // QuestionExportService.java  (thêm ngay dưới PracticeHeader)
    // Thay ExamHeader cũ bằng phiên bản có thêm institute/faculty/mauLabel
    // Thay ExamHeader cũ bằng phiên bản có thêm institute/faculty/mauLabel
    public static record ExamHeader(
            String institute,   // ví dụ: "HỌC VIỆN CÔNG NGHỆ BƯU CHÍNH VIỄN THÔNG"
            String faculty,     // KHOA: (vd "Công nghệ thông tin")
            String program,     // BỘ MÔN / Chuyên ngành (vd "Khoa học máy tính")
            String subjectName,
            String subjectCode,
            String semester,    // "I", "II", "Hè", ...
            String academicYear,// "2024-2025"
            String classes,     // "D18CN, D18AT"
            String duration,    // "90 phút"
            Integer paperNo,    // null -> ẩn
            String examForm,    // "Hình thức thi viết"
            String mauLabel     // "Mẫu 3a" (có thể null)
    ) {}


    public QuestionExportService(QuestionService questionService) {
        this.questionService = questionService;
    }

    /* ==================== PDF ==================== */

    public byte[] exportQuestionsToPdf(List<Long> questionIds, boolean includeAnswers) {
        List<QuestionDTO> questions = questionService.findByIds(questionIds);
        if (questions.isEmpty()) throw new IllegalStateException("No questions for IDs: " + questionIds);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            FontProgram textProg = FontProgramFactory.createFont(FONT_TEXT);
            PdfFont text = PdfFontFactory.createFont(textProg, PdfEncodings.IDENTITY_H);
            FontProgram mathProg = FontProgramFactory.createFont(FONT_MATH);
            PdfFont math = PdfFontFactory.createFont(mathProg, PdfEncodings.IDENTITY_H);

            FontSet fs = new FontSet();
            fs.addFont(FONT_TEXT);
            fs.addFont(FONT_MATH);
            fs.addFont("src/main/resources/fonts/NotoSansSymbols2-Regular.ttf");
            FontProvider provider = new FontProvider(fs);
            doc.setFontProvider(provider);
            doc.setProperty(Property.FONT, new String[]{"Noto Sans", "Noto Sans Symbols 2", "Noto Sans Math"});

            final float PT_TEXT     = 11f;
            final float PT_LEADING  = 15.5f;
            final float PT_EQ_SIZE  = 11.5f;

            int idx = 0;
            for (QuestionDTO q : questions) {
                doc.add(new Paragraph("Question " + (++idx))
                        .setBold().setFontSize(14)
                        .setMarginTop(6).setMarginBottom(4));

                String text1 = prettyMathSpaces(normalizeForPdfKeepNewlines(q.getContent()));
                for (String line : text1.split("\\R", -1)) {
                    Paragraph p = buildLineParagraph(PT_LEADING);
                    addInlineTeXLitePng(p, line, PT_EQ_SIZE);
                    doc.add(p);
                }

                if (q.getImages() != null && !q.getImages().isEmpty()) {
                    for (var imgDto : q.getImages()) addPdfImage(doc, imgDto.getUrl());
                } else if (q.getImageUrl() != null) {
                    addPdfImage(doc, q.getImageUrl());
                }

                if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                    addOptionLine(doc, "a) " + prettyMathSpaces(normalizeForPdfKeepNewlines(q.getOptionA())), PT_EQ_SIZE, PT_LEADING);
                    addOptionLine(doc, "b) " + prettyMathSpaces(normalizeForPdfKeepNewlines(q.getOptionB())), PT_EQ_SIZE, PT_LEADING);
                    addOptionLine(doc, "c) " + prettyMathSpaces(normalizeForPdfKeepNewlines(q.getOptionC())), PT_EQ_SIZE, PT_LEADING);
                    addOptionLine(doc, "d) " + prettyMathSpaces(normalizeForPdfKeepNewlines(q.getOptionD())), PT_EQ_SIZE, PT_LEADING);
                }

                if (includeAnswers) {
                    String ans = (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE)
                            ? q.getAnswer() : q.getAnswerText();
                    if (hasText(ans)) {
                        Paragraph p = buildLineParagraph(PT_LEADING);
                        addInlineTeXLitePng(p, "Answer: " + prettyMathSpaces(safeText(ans)), PT_EQ_SIZE);
                        doc.add(p);
                    }
                }

                doc.add(new Paragraph().setFixedLeading(6f).setMargin(0).setPadding(0));
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed", e);
        }
    }

    // ======= PDF helpers (nguyên vẹn như bản bạn gửi) =======

    private void addInlineTeXLitePng(Paragraph par, String line, float eqPt) {
        if (line == null) { par.add(""); return; }

        int pos = 0;
        while (pos < line.length()) {
            int best = line.length();
            String token = null;

            int iOver = indexOfCI(line, "overline(", pos);
            int iFrac = indexOfCI(line, "frac(", pos);
            int iSqrt = indexOfCI(line, "sqrt(", pos);
            int iRoot = indexOfCI(line, "root(", pos);
            int iCases= indexOfCI(line, "cases(", pos);

            if (iOver >= 0 && iOver < best) { best = iOver; token = "overline"; }
            if (iFrac >= 0 && iFrac < best) { best = iFrac; token = "frac"; }
            if (iSqrt >= 0 && iSqrt < best) { best = iSqrt; token = "sqrt"; }
            if (iRoot >= 0 && iRoot < best) { best = iRoot; token = "root"; }
            if (iCases>= 0 && iCases< best) { best = iCases; token = "cases"; }

            if (token == null) {
                par.add(line.substring(pos));
                return;
            }

            if (best > pos) par.add(line.substring(pos, best));

            switch (token) {
                case "overline": {
                    int start = best + "overline(".length();
                    int end = findMatchingParen(line, start);
                    if (end < 0) { par.add(line.substring(best)); return; }
                    String body = line.substring(start, end);
                    Image img = latexToPngImageTight("\\overline{" + normalizeBodyForLatex(body) + "}", eqPt);
                    if (img != null) { img.setMarginTop(-1.2f); img.setMarginBottom(-1.2f); par.add(img); }
                    else par.add(line.substring(best, end+1));
                    pos = end + 1;
                    break;
                }
                case "sqrt": {
                    int start = best + "sqrt(".length();
                    int end = findMatchingParen(line, start);
                    if (end < 0) { par.add(line.substring(best)); return; }
                    String arg = line.substring(start, end);
                    Image img = latexToPngImageTight("\\sqrt{" + normalizeBodyForLatex(arg) + "}", eqPt);
                    if (img != null) { img.setMarginTop(-1.2f); img.setMarginBottom(-1.2f); par.add(img); }
                    else par.add(line.substring(best, end+1));
                    pos = end + 1;
                    break;
                }
                case "root": {
                    int startDeg = best + "root(".length();
                    int endDeg = findMatchingParen(line, startDeg);
                    if (endDeg < 0 || endDeg+1 >= line.length() || line.charAt(endDeg+1)!='(') { par.add(line.substring(best)); return; }
                    int startBody = endDeg + 2;
                    int endBody = findMatchingParen(line, startBody);
                    if (endBody < 0) { par.add(line.substring(best)); return; }
                    String deg  = line.substring(startDeg, endDeg);
                    String body = line.substring(startBody, endBody);
                    Image img = latexToPngImageTight("\\sqrt[" + normalizeBodyForLatex(deg) + "]{" + normalizeBodyForLatex(body) + "}", eqPt);
                    if (img != null) { img.setMarginTop(-1.2f); img.setMarginBottom(-1.2f); par.add(img); }
                    else par.add(line.substring(best, endBody+1));
                    pos = endBody + 1;
                    break;
                }
                case "frac": {
                    int start = best + "frac(".length();
                    int end = findMatchingParen(line, start);
                    if (end < 0) { par.add(line.substring(best)); return; }
                    String inside = line.substring(start, end);
                    List<String> ab = splitTopLevel(inside, ',');
                    if (ab.size() != 2) { par.add(line.substring(best, end+1)); pos = end+1; break; }
                    Image img = latexToPngImageTight("\\frac{" + normalizeBodyForLatex(ab.get(0)) + "}{" + normalizeBodyForLatex(ab.get(1)) + "}", eqPt);
                    if (img != null) { img.setMarginTop(-1.2f); img.setMarginBottom(-1.2f); par.add(img); }
                    else par.add(line.substring(best, end+1));
                    pos = end + 1;
                    break;
                }
                case "cases": {
                    int start = best + "cases(".length();
                    int end = findMatchingParen(line, start);
                    if (end < 0) { par.add(line.substring(best)); return; }
                    List<String> ls = splitTopLevel(line.substring(start, end), ';');
                    StringBuilder latex = new StringBuilder("\\left\\{\\begin{array}{l}");
                    for (int k=0;k<ls.size();k++){
                        if (k>0) latex.append("\\\\");
                        latex.append(normalizeBodyForLatex(ls.get(k)));
                    }
                    latex.append("\\end{array}\\right.");
                    Image img = latexToPngImageTight(latex.toString(), eqPt);
                    if (img != null) { img.setMarginTop(-1.2f); img.setMarginBottom(-1.2f); par.add(img); }
                    else par.add(line.substring(best, end+1));
                    pos = end + 1;
                    break;
                }
            }
        }
    }

    private static BufferedImage trimTransparent(BufferedImage src, int pad) {
        int w = src.getWidth(), h = src.getHeight();
        int top = 0, bottom = h - 1, left = 0, right = w - 1;

        outer:
        for (; top < h; top++) {
            for (int x = 0; x < w; x++) if ((src.getRGB(x, top) >>> 24) != 0) break outer;
        }
        outer:
        for (; bottom >= 0; bottom--) {
            for (int x = 0; x < w; x++) if ((src.getRGB(x, bottom) >>> 24) != 0) break outer;
        }
        outer:
        for (; left < w; left++) {
            for (int y = top; y <= bottom; y++) if ((src.getRGB(left, y) >>> 24) != 0) break outer;
        }
        outer:
        for (; right >= 0; right--) {
            for (int y = top; y <= bottom; y++) if ((src.getRGB(right, y) >>> 24) != 0) break outer;
        }

        if (top > bottom || left > right) return src;
        top = Math.max(0, top - pad);
        left = Math.max(0, left - pad);
        bottom = Math.min(h - 1, bottom + pad);
        right = Math.min(w - 1, right + pad);

        return src.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private Paragraph buildLineParagraph(float fixedLeading) {
        return new Paragraph()
                .setFixedLeading(fixedLeading)
                .setMarginTop(0).setMarginBottom(2)
                .setPaddingTop(0).setPaddingBottom(0);
    }

    private void addOptionLine(Document doc, String text, float eqPt, float fixedLeading) {
        if (!hasText(text)) return;
        Paragraph p = buildLineParagraph(fixedLeading);
        addInlineTeXLitePng(p, text, eqPt);
        doc.add(p);
    }

    private Image latexToPngImageTight(String latex, float targetPt) {
        try {
            float scale = 3.0f;
            float jlmSize = targetPt * scale;

            TeXFormula f = new TeXFormula(latex);
            org.scilab.forge.jlatexmath.TeXIcon icon =
                    f.createTeXIcon(org.scilab.forge.jlatexmath.TeXConstants.STYLE_TEXT, jlmSize);
            icon.setInsets(new java.awt.Insets(0,0,0,0));

            int w = Math.max(1, icon.getIconWidth());
            int h = Math.max(1, icon.getIconHeight());
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setColor(java.awt.Color.BLACK);
            icon.paintIcon(new javax.swing.JLabel(), g2, 0, 0);
            g2.dispose();

            BufferedImage trimmed = trimTransparent(bi, 2);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(trimmed, "png", bos);

            Image img = new Image(ImageDataFactory.create(bos.toByteArray()));
            img.setAutoScale(false);
            float ph = trimmed.getHeight() / scale;
            float pw = trimmed.getWidth()  / scale;
            img.setHeight(ph);
            img.setWidth(pw);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    private int indexOfCI(String s, String needle, int from) {
        int n = needle.length();
        for (int i = from; i + n <= s.length(); i++)
            if (s.regionMatches(true, i, needle, 0, n)) return i;
        return -1;
    }

    private int findMatchingParen(String s, int start) {
        int depth = 1;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private String normalizeBodyForLatex(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        t = t.replaceAll("([A-Za-z0-9])\\s*\\^\\s*([A-Za-z0-9])", "$1^{$2}");
        t = t.replaceAll("([A-Za-z0-9])\\s*_\\s*([A-Za-z0-9])", "$1_{$2}");
        return t.replace("∧","\\wedge").replace("∨","\\vee")
                .replace("⇒","\\Rightarrow").replace("≡","\\equiv")
                .replace("≤","\\leq").replace("≥","\\geq");
    }

    private void addPdfImage(Document doc, String url) {
        try {
            Image img = new Image(ImageDataFactory.create(new URL(forcePng(url))));
            img.scaleToFit(400, 300);
            doc.add(img);
        } catch (Exception e) {
            doc.add(new Paragraph("Error loading image: " + url));
        }
    }

    /* ==================== WORD ==================== */

    public byte[] exportQuestionsToWordPractice(List<Long> questionIds,
                                                boolean includeAnswers,
                                                PracticeHeader header) throws IOException {
        List<QuestionDTO> questions = questionService.findByIds(questionIds);
        if (questions.isEmpty()) throw new IllegalStateException("No questions found for IDs: " + questionIds);

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Header ôn tập
            writePracticeHeader(doc, header);

            // sort theo chapter rồi id
            questions.sort(Comparator.comparingInt(QuestionDTO::getChapter)
                    .thenComparing(QuestionDTO::getId));

            int globalIdx = 0;
            Map<Integer,Integer> inChapterCounter = new HashMap<>();
            Integer lastChapter = null;

            for (QuestionDTO q : questions) {
                int chapter = q.getChapter(); // int: 0 nghĩa là không thuộc chương
                if (chapter > 0 && (lastChapter == null || !lastChapter.equals(chapter))) {
                    writeChapterHeading(doc, chapter);
                }

                String title;
                if (chapter > 0) {
                    int idxInChap = inChapterCounter.merge(chapter, 1, Integer::sum);
                    title = chapter + "." + idxInChap;
                } else {
                    title = "Câu " + (++globalIdx);
                }
                writeQuestionTitle(doc, title);

                // Nội dung: tách dòng, text TNR13; OMML Cambria Math 13; sqrt ẩn bậc căn
                String content = prettyMathSpaces(safeText(q.getContent()));
                writeContentSmart(doc, content);

                // Ảnh
                if (q.getImages() != null && !q.getImages().isEmpty()) {
                    for (var imgDto : q.getImages()) addWordImage(doc, imgDto.getUrl());
                } else if (q.getImageUrl() != null) {
                    addWordImage(doc, q.getImageUrl());
                }

                // Phương án
                if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                    writeOptionIfPresent(doc, "a) ", q.getOptionA());
                    writeOptionIfPresent(doc, "b) ", q.getOptionB());
                    writeOptionIfPresent(doc, "c) ", q.getOptionC());
                    writeOptionIfPresent(doc, "d) ", q.getOptionD());
                }

                // Đáp án (nếu chọn)
                if (includeAnswers) {
                    String ans = (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE) ? q.getAnswer() : q.getAnswerText();
                    if (hasText(ans)) writeContentSmart(doc, "Answer: " + prettyMathSpaces(safeText(ans)));
                }

                doc.createParagraph(); // spacer
                lastChapter = chapter;
            }

            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // QuestionExportService.java  (thêm mới)
    public byte[] exportQuestionsToWordExam(List<Long> questionIds,
                                            boolean includeAnswers,
                                            ExamHeader header) throws IOException {
        List<QuestionDTO> questions = questionService.findByIds(questionIds);
        if (questions.isEmpty()) throw new IllegalStateException("No questions found for IDs: " + questionIds);

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // ===== Header đề thi =====
            writeExamHeader(doc, header);

            // ===== Nội dung câu hỏi (kiểu “Câu 1”, không theo chapter) =====
            int idx = 0;
            for (QuestionDTO q : questions) {
                writeQuestionTitle(doc, "Câu " + (++idx));

                String content = prettyMathSpaces(safeText(q.getContent()));
                writeContentSmart(doc, content);

                if (q.getImages() != null && !q.getImages().isEmpty()) {
                    for (var imgDto : q.getImages()) addWordImage(doc, imgDto.getUrl());
                } else if (q.getImageUrl() != null) {
                    addWordImage(doc, q.getImageUrl());
                }

                if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                    writeOptionIfPresent(doc, "a) ", q.getOptionA());
                    writeOptionIfPresent(doc, "b) ", q.getOptionB());
                    writeOptionIfPresent(doc, "c) ", q.getOptionC());
                    writeOptionIfPresent(doc, "d) ", q.getOptionD());
                }

                if (includeAnswers) {
                    String ans = (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE) ? q.getAnswer() : q.getAnswerText();
                    if (hasText(ans)) writeContentSmart(doc, "Answer: " + prettyMathSpaces(safeText(ans)));
                }

                doc.createParagraph(); // 1 dòng trống giữa các câu
            }

            // ===== Footer đề thi =====
            writeExamFooter(doc);

            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // Nén chữ (Font Advanced → Spacing condensed -0.5pt)
    private static void applyCondensed05pt(XWPFRun r) {
        var rpr = r.getCTR().isSetRPr() ? r.getCTR().getRPr() : r.getCTR().addNewRPr();

        // Safe approach - always create new spacing element
        var sp = rpr.addNewSpacing();
        sp.setVal(BigInteger.valueOf(-10));     // -0.5pt = -10 twips
    }

    // ===== helpers (nếu bạn đã có thì giữ nguyên) =====
    private static String up(String s){ return s==null? "": s.toUpperCase(java.util.Locale.ROOT); }

    private static void setParaSpacing(XWPFParagraph p, Integer beforePt, Integer afterPt, double lineMult){
        var pr = p.getCTP().isSetPPr()? p.getCTP().getPPr(): p.getCTP().addNewPPr();
        var sp = pr.isSetSpacing()? pr.getSpacing(): pr.addNewSpacing();
        if (beforePt != null) sp.setBefore(BigInteger.valueOf(beforePt * 20L));
        if (afterPt  != null) sp.setAfter (BigInteger.valueOf(afterPt  * 20L));
        sp.setLine(BigInteger.valueOf(Math.round(lineMult * 240)));
        sp.setLineRule(STLineSpacingRule.AUTO);
    }

    private static void addRun(XWPFParagraph p, String text, boolean bold, int fontPt, boolean upper){
        XWPFRun r = p.createRun();
        r.setFontFamily("Times New Roman");
        r.setFontSize(fontPt);
        r.setBold(bold);
        r.setText(upper ? up(text) : (text==null? "" : text));
        applyCondensed05pt(r);
    }

    // ===== HEADER ĐỀ THI (chuẩn theo yêu cầu) =====
    private void writeExamHeader(XWPFDocument doc, ExamHeader h) {
        // "Mẫu 3a" – phải, 11pt
        if (hasText(h.mauLabel())) {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.RIGHT);
            setParaSpacing(p, null, 8, 1.08);
            XWPFRun r = p.createRun();
            r.setFontFamily("Times New Roman"); r.setFontSize(11); r.setItalic(true);
            r.setText(h.mauLabel());
        }

        // Bảng 2 cột 58/42, không viền
        XWPFTable head = doc.createTable(1, 2);
        head.setWidth("100%");
        head.removeBorders();
        XWPFTableRow r0 = head.getRow(0);
        XWPFTableCell left  = r0.getCell(0); left.setWidth("52%"); left.removeParagraph(0);
        XWPFTableCell right = r0.getCell(1); right.setWidth("48%"); right.removeParagraph(0);
        left.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        right.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

        // ==== CỘT TRÁI: HỌC VIỆN / KHOA / BỘ MÔN (font 11, 1.08, condensed −0.5pt) ====
        XWPFParagraph pInst = left.addParagraph();
        pInst.setAlignment(ParagraphAlignment.CENTER);
        setParaSpacing(pInst, null, 8, 1.08);
        addRun(pInst, up(h.institute()), false, 11, false);

        XWPFParagraph pbm = left.addParagraph();
        pbm.setAlignment(ParagraphAlignment.CENTER);
        setParaSpacing(pbm, 6, 6, 1.08);
        addRun(pbm, "KHOA: ", false, 11, true);
        addRun(pbm, h.program(), false, 11, true);

        XWPFParagraph pk = left.addParagraph();
        pk.setAlignment(ParagraphAlignment.CENTER);
        setParaSpacing(pk, 6, 6, 1.08);
        addRun(pk, "BỘ MÔN: ", true, 11, true);
        addRun(pk, h.faculty(), false, 11, true);
        // (bỏ vạch dưới theo yêu cầu)

        // ==== CỘT PHẢI: 2 dòng trống, Title + (Hình thức thi viết) ====
        for (int i = 0; i < 2; i++) {
            XWPFParagraph blank = right.addParagraph();
            blank.setAlignment(ParagraphAlignment.CENTER);
            setParaSpacing(blank, 0, 0, 1.08);
            blank.createRun().setText("");
        }

        XWPFParagraph pt = right.addParagraph();
        pt.setAlignment(ParagraphAlignment.CENTER);
        setParaSpacing(pt, null, 8, 1.08);
        XWPFRun rt = pt.createRun();
        rt.setFontFamily("Times New Roman"); rt.setFontSize(14); rt.setBold(true);
        rt.setText(up("ĐỀ THI KẾT THÚC HỌC PHẦN"));

        XWPFParagraph pf = right.addParagraph();
        pf.setAlignment(ParagraphAlignment.CENTER);
        setParaSpacing(pf, 0, 8, 1.08); // remove space before
        XWPFRun rf = pf.createRun();
        rf.setFontFamily("Times New Roman"); rf.setFontSize(14); rf.setBold(true); // không italic, giữ nguyên chữ hoa đầu câu
        rf.setText("(" + nullToEmpty(h.examForm()) + ")");

        // ==== Học phần (bold cả câu) ====
        // ==== Bảng thông tin: Row0 = Học phần (merge 2 cột), Row1 = Lớp | Thời gian thi ====
        XWPFTable info = doc.createTable(2, 2);
        info.setWidth("100%");
        info.removeBorders();

// --- Row 0: merge 2 cột cho "Học phần…" ---
        XWPFTableRow rSubj = info.getRow(0);
        XWPFTableCell subL = rSubj.getCell(0);
        XWPFTableCell subR = rSubj.getCell(1);

// merge ngang bằng HMerge
        var prL = subL.getCTTc().getTcPr(); if (prL == null) prL = subL.getCTTc().addNewTcPr();
        (prL.getHMerge() == null ? prL.addNewHMerge() : prL.getHMerge()).setVal(STMerge.RESTART);
        var prR = subR.getCTTc().getTcPr(); if (prR == null) prR = subR.getCTTc().addNewTcPr();
        (prR.getHMerge() == null ? prR.addNewHMerge() : prR.getHMerge()).setVal(STMerge.CONTINUE);

// nội dung "Học phần" (bold cả câu) + tăng after để hàng cao hơn, center Oy
        subL.removeParagraph(0);
        XWPFParagraph pSubject = subL.addParagraph();
        pSubject.setAlignment(ParagraphAlignment.LEFT);
        setParaSpacing(pSubject, 8, 8, 1.08);           // trước 8pt, sau 8pt
        XWPFRun rSub = pSubject.createRun();
        rSub.setFontFamily("Times New Roman"); rSub.setFontSize(12); rSub.setBold(true);
        rSub.setText("Học phần: " + nullToEmpty(h.subjectName())
                + " (Học kỳ " + nullToEmpty(h.semester())
                + " năm học " + nullToEmpty(h.academicYear()) + ")");

        subL.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        subR.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

// --- Row 1: "Lớp" (trái 58%) | "Thời gian thi" (phải 42%, center) ---
        XWPFTableRow rInfo = info.getRow(1);
        XWPFTableCell cL = rInfo.getCell(0);  cL.setWidth("52%");
        XWPFTableCell cR = rInfo.getCell(1);  cR.setWidth("48%");
        cL.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        cR.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

// Lớp — bold cả câu, thêm khoảng trước/sau cho cân đối
        cL.removeParagraph(0);
        XWPFParagraph pL = cL.addParagraph();
        pL.setAlignment(ParagraphAlignment.LEFT);
        setParaSpacing(pL, 6, 6, 1.08);
        XWPFRun rL = pL.createRun();
        rL.setFontFamily("Times New Roman"); rL.setFontSize(12); rL.setBold(true);
        rL.setText("Lớp: " + nullToEmpty(h.classes()));

// Thời gian thi — label bold, giá trị thường; căn giữa
        cR.removeParagraph(0);
        XWPFParagraph pR = cR.addParagraph();
        pR.setAlignment(ParagraphAlignment.CENTER);
        setParaSpacing(pR, 6, 6, 1.08);
        XWPFRun rR1 = pR.createRun();
        rR1.setFontFamily("Times New Roman"); rR1.setFontSize(12); rR1.setBold(true);
        rR1.setText("Thời gian thi:   ");
        XWPFRun rR2 = pR.createRun();
        rR2.setFontFamily("Times New Roman"); rR2.setFontSize(12); rR2.setBold(false);
        rR2.setText(nullToEmpty(h.duration()));

// Đề số — label bold, giá trị thường
        if (h.paperNo() != null) {
            XWPFParagraph pNo = doc.createParagraph();
            pNo.setAlignment(ParagraphAlignment.CENTER);
            setParaSpacing(pNo, 8, 8, 1.08);
            XWPFRun rNo1 = pNo.createRun();
            rNo1.setFontFamily("Times New Roman"); rNo1.setFontSize(12); rNo1.setBold(true);
            rNo1.setText("Đề số: ");
            XWPFRun rNo2 = pNo.createRun();
            rNo2.setFontFamily("Times New Roman"); rNo2.setFontSize(12); rNo2.setBold(false);
            rNo2.setText(String.valueOf(h.paperNo()));
        }

        // spacer nhỏ sau header
        XWPFParagraph sp = doc.createParagraph();
        setParaSpacing(sp, 4, 12, 1.08);
    }

    private String nullToEmpty(String s){ return s==null? "" : s; }

    private void writeExamFooter(XWPFDocument doc) {
        // Đường kẻ mảnh và “Ghi chú: …”
        XWPFParagraph pRule = doc.createParagraph();
        pRule.setBorderBottom(Borders.SINGLE);
        setSpacing(pRule, 0, 120, 1.0);

        XWPFParagraph pNote = doc.createParagraph();
        setSpacing(pNote, 0, 200, 1.0);
        newRun(pNote, true, 12).setText("Ghi chú: ");
        XWPFRun rn = newRun(pNote, false, 12);
        rn.setItalic(true);
        rn.setText("Sinh viên không được tham khảo tài liệu");

        // Họ tên SV … Lớp … Phòng thi …
        XWPFParagraph pInfo1 = doc.createParagraph();
        setSpacing(pInfo1, 120, 120, 1.0);
        newRun(pInfo1, false, 12).setText("Họ tên SV: ...................................   Lớp: ........................   Phòng thi: ............");

        // Ký tên …
        XWPFParagraph pInfo2 = doc.createParagraph();
        setSpacing(pInfo2, 0, 0, 1.0);
        newRun(pInfo2, false, 12).setText("Ký tên: ........................................");
    }

    /* ---------- Word helpers ---------- */

    // Title “Câu 1” / “1.1”
    private void writePracticeHeader(XWPFDocument doc, PracticeHeader h) {
        // Tiêu đề
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        setSpacing(title, 0, 600, 1.0); // after ~30pt
        newRun(title, true, 16).setText(h.bankTitle());

        // Bảng 2×3: [label+value] [gutter] [label+value]
        XWPFTable tbl = doc.createTable(2, 3);
        tbl.removeBorders();
        setTableFixed3Cols(tbl, 5500, 100, 5500); // w1, gutter, w3 (twips)

        // hàng 1
        fillHeaderCell(tbl.getRow(0).getCell(0), "Tên học phần: ", h.subjectName());
        makeGutter(tbl.getRow(0).getCell(1)); // ô trống đúng nghĩa
        fillHeaderCell(tbl.getRow(0).getCell(2), "Mã học phần: ", h.subjectCode());

        // hàng 2
        fillHeaderCell(tbl.getRow(1).getCell(0), "Ngành đào tạo: ", h.program()==null? "" : h.program());
        makeGutter(tbl.getRow(1).getCell(1));
        fillHeaderCell(tbl.getRow(1).getCell(2), "Trình độ đào tạo: ", h.level());

        // chừa khoảng dưới toàn khối header
        XWPFParagraph spacer = doc.createParagraph();
        setSpacing(spacer, 0, 600, 1.0);
    }

    private void setTableFixed3Cols(XWPFTable tbl, int w1, int gutter, int w3) {
        CTTbl ctTbl = tbl.getCTTbl();

        // tblPr
        CTTblPr pr = ctTbl.getTblPr();
        if (pr == null) pr = ctTbl.addNewTblPr();

        // layout fixed => Word không auto-fit độ rộng cột
        CTTblLayoutType layout = pr.getTblLayout();
        if (layout == null) layout = pr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);

        // grid (xóa grid cũ nếu có, rồi set lại 3 cột)
        CTTblGrid grid = ctTbl.getTblGrid();
        if (grid == null) {
            grid = ctTbl.addNewTblGrid();
        } else {
            while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        }
        grid.addNewGridCol().setW(BigInteger.valueOf(w1));
        grid.addNewGridCol().setW(BigInteger.valueOf(gutter));
        grid.addNewGridCol().setW(BigInteger.valueOf(w3));

        // set width cho từng ô khớp grid (an toàn)
        for (int r = 0; r < tbl.getNumberOfRows(); r++) {
            XWPFTableRow row = tbl.getRow(r);
            setCellWidth(row.getCell(0), w1);
            setCellWidth(row.getCell(1), gutter);
            setCellWidth(row.getCell(2), w3);
        }
    }

    private void setCellWidth(XWPFTableCell c, int twips) {
        if (c == null) return;
        CTTcPr tcPr = c.getCTTc().getTcPr();
        if (tcPr == null) tcPr = c.getCTTc().addNewTcPr();
        CTTblWidth w = tcPr.getTcW();
        if (w == null) w = tcPr.addNewTcW();
        w.setType(STTblWidth.DXA);
        w.setW(BigInteger.valueOf(twips));
    }

    private void makeGutter(XWPFTableCell c) {
        if (c == null) return;
        // bỏ mọi paragraph để nó thực sự trống
        while (c.getParagraphs().size() > 0) c.removeParagraph(0);
    }

    private void fillHeaderCell(XWPFTableCell cell, String label, String value) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        setSpacing(p, 0, 0, 1.0);
        newRun(p, true, 12).setText(label);
        newRun(p, false, 12).setText(value == null ? "" : value);
    }

    // Giãn dòng/đệm cho paragraph theo twips (1pt = 20 twips)
    private void setSpacing(XWPFParagraph p, int beforeTwips, int afterTwips, double line) {
        var pr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        var sp = pr.isSetSpacing() ? pr.getSpacing() : pr.addNewSpacing();
        if (beforeTwips > 0) sp.setBefore(BigInteger.valueOf(beforeTwips)); else if (sp.isSetBefore()) sp.unsetBefore();
        if (afterTwips  > 0) sp.setAfter (BigInteger.valueOf(afterTwips )); else if (sp.isSetAfter())  sp.unsetAfter();
        // 1.0 = 240, 1.15 ≈ 276, 1.5 = 360
        sp.setLine(BigInteger.valueOf(Math.round(line * 240)));
        sp.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.AUTO);
    }

    private void writeChapterHeading(XWPFDocument doc, int chapter) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        tuneParagraphSpacing(p);
        XWPFRun r = newRun(p, true, 14);
        r.setText("CHƯƠNG " + chapter);
    }

    private void writeQuestionTitle(XWPFDocument doc, String s) {
        XWPFParagraph p = doc.createParagraph();
        tuneParagraphSpacing(p);
        XWPFRun r = newRun(p, true, 13);
        r.setText(s);
    }

    // newRun: Times New Roman 13 mặc định (override khi truyền size)
    private XWPFRun newRun(XWPFParagraph p, boolean bold, int fontSize) {
        XWPFRun r = p.createRun();
        r.setBold(bold);
        if (fontSize > 0) r.setFontSize(fontSize);
        r.setFontFamily("Times New Roman");
        return r;
    }

    // writeContentSmart / writeOptionIfPresent giữ nguyên logic cũ,
    // CHỈ lưu ý: runFactory set TNR 13 để đồng bộ
    private void writeContentSmart(XWPFDocument doc, String text) {
        if (text == null) text = "";
        for (String line : text.split("\\R", -1)) {
            XWPFParagraph p = doc.createParagraph();
            tuneParagraphSpacing(p);
            emitMathAware(p, line, (s) -> {
                XWPFRun r = p.createRun();
                r.setFontFamily("Times New Roman");
                r.setFontSize(13);
                return r;
            });
        }
    }

    private void writeOptionIfPresent(XWPFDocument doc, String prefix, String val) {
        if (!hasText(val)) return;
        String line = prefix + prettyMathSpaces(safeText(val));
        XWPFParagraph p = doc.createParagraph();
        tuneParagraphSpacing(p);
        emitMathAware(p, line, (s) -> {
            XWPFRun r = p.createRun();
            r.setFontFamily("Times New Roman");
            r.setFontSize(13);
            return r;
        });
    }

    private void tuneParagraphSpacing(XWPFParagraph p) {
        setSpacing(p, 0, 0, 1.3); // after ~8pt, line spacing 1.3
    }

    private void addWordImage(XWPFDocument doc, String url) {
        String u = forcePng(url);
        try (var is = new URL(u).openStream()) {
            XWPFParagraph ip = doc.createParagraph();
            tuneParagraphSpacing(ip);
            XWPFRun ir = newRun(ip, false, 0);
            ir.addPicture(is, XWPFDocument.PICTURE_TYPE_PNG, "image",
                    Units.toEMU(400), Units.toEMU(300));
        } catch (Exception e) {
            XWPFParagraph ep = doc.createParagraph();
            tuneParagraphSpacing(ep);
            newRun(ep, false, 0).setText("Error loading image: " + url);
        }
    }

    /* ==================== helpers ==================== */

    private boolean hasText(String s) {
        return s != null && !s.isBlank() && !"null".equalsIgnoreCase(s.trim());
    }

    private String prettyMathSpaces(String s) {
        if (s == null) return null;
        String out = s;
        out = out.replaceAll("\\s*([∧∨≡⇒=+\\-×÷])\\s*", " $1 ");
        out = out.replaceAll("\\s{2,}", " ").trim();
        return out;
    }

    private String safeText(String s) {
        if (s == null) return "";
        String t = TextNormalize.remapPUA(s);
        t = TextNormalize.normalizePreserveNewlines(t);
        t = TextNormalize.normalizeSuperSubRuns(t);
        return t;
    }

    private String forcePng(String url) {
        if (url == null) return null;
        int i = url.indexOf("/upload/");
        if (i < 0) return url;
        return url.substring(0, i + 8) + "f_png/" + url.substring(i + 8);
    }

    // ====== các helper nhỏ cho PDF (đặt cuối file cho gọn) ======

    private static String normalizeForPdfKeepNewlines(String s) {
        if (s == null) return "";
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC);

        s = s.replace('\u00A0',' ')
                .replace('\u1680',' ')
                .replace('\u2000',' ').replace('\u2001',' ').replace('\u2002',' ')
                .replace('\u2003',' ').replace('\u2004',' ').replace('\u2005',' ')
                .replace('\u2006',' ').replace('\u2007',' ').replace('\u2008',' ')
                .replace('\u2009',' ').replace('\u200A',' ')
                .replace('\u202F',' ').replace('\u205F',' ').replace('\u3000',' ');

        s = s.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "");

        s = s.replace("\uFEFF","")
                .replace("\u200B","")
                .replace("\u2060","");

        s = s.replace('','∨').replace('','∧').replace('','≥')
                .replace('','≤').replace('','=').replace('','→')
                .replace('','↔').replace('','≡').replace('','≈').replace('',' ');

        return s;
    }
}
