package com.exam.examserver.service.import_export;

import com.exam.examserver.dto.importing.ExtractResult;
import com.exam.examserver.util.TextNormalize;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class PdfOmmlExtractor {

    /** API cũ: DocxOmmlExtractor.extractPdf sẽ gọi vào đây */
    public static ExtractResult extractPdf(InputStream is) throws IOException {
        try (PDDocument doc = PDDocument.load(is)) {
            // 1) Thử strip text “sống”
            NativeTextAndImages out = extractNative(doc);

            if (looksLikeScanned(out)) {
                // 2) Fallback OCR nếu text quá ít / vô nghĩa
                NativeTextAndImages ocr = extractByOCR(doc);
                return new ExtractResult(ocr.text, ocr.images);
            }
            return new ExtractResult(out.text, out.images);
        }
    }

    /* ============================================================
       Tầng 1: PDF “sống” (text + ảnh + tọa độ) với layout cơ bản
       ============================================================ */

    private static class NativeTextAndImages {
        String text;
        List<byte[]> images = new ArrayList<>();
    }

    private static NativeTextAndImages extractNative(PDDocument doc) throws IOException {
        // A) thu ảnh + toạ độ theo trang
        ImageCollector collector = new ImageCollector(doc);
        collector.collect();

        // B) strip text + thu lines
        LayoutStripper stripper = new LayoutStripper();
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");
        stripper.setWordSeparator(" ");
        String raw = stripper.getText(doc);

        // C) chèn placeholder theo dòng (cuối dòng)
        String merged = insertImagePlaceholders(raw, collector, stripper);


        NativeTextAndImages res = new NativeTextAndImages();
        res.text = postNormalize(merged);
        res.images.addAll(collector.images);
        return res;
    }

    private static String postNormalize(String s) {
        // Bảo toàn xuống dòng, gọn khoảng trắng, chuẩn hoá toán học nhẹ
        if (s == null) return "";
        s = s.replaceAll("[\\t\\u00A0]+", " ");
        s = s.replaceAll(" +\\n", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        s = TextNormalize.normalizePreserveNewlines(s);
        s = TextNormalize.normalizeSoftMath(s);
        return s.trim();
    }

    /* --- Heuristic: PDF scan nếu text “nghèo nàn” --- */
    private static boolean looksLikeScanned(NativeTextAndImages out) {
        if (out == null || out.text == null) return true;
        String t = out.text;
        int len = t.length();
        if (len < 200) return true;

        int good = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c) || ".,;:!?()[]{}+-=×÷/%°’'\"–—… ".indexOf(c) >= 0 ||
                    Character.getType(c) == Character.OTHER_LETTER) {
                good++;
            }
        }
        double ratio = good * 1.0 / Math.max(1, len);
        return ratio < 0.6;
    }

    /* ======================
       Layout-aware stripper
       ====================== */
    private static class LayoutStripper extends PDFTextStripper {
        static class Line {
            final String text;
            final double y;     // baseline trung bình của dòng (đơn vị “user space” của PDFBox)
            Line(String text, double y) { this.text = text; this.y = y; }
        }
        final Map<Integer, List<Line>> pageLines = new HashMap<>();
        private int pageIndex = -1;
        private final StringBuilder pageBuf = new StringBuilder();

        LayoutStripper() throws IOException { super(); }

        @Override protected void startPage(PDPage page) throws IOException {
            pageIndex++;
            pageLines.put(pageIndex, new ArrayList<>());
            pageBuf.setLength(0);
            super.startPage(page);
        }

        @Override protected void endPage(PDPage page) throws IOException {
            // kết thúc trang: thêm FF để split
            getOutput().write(pageBuf.toString());
            getOutput().write('\f');
            super.endPage(page);
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
            if (textPositions == null || textPositions.isEmpty()) return;

            // baseline trung bình của dòng:
            double avgBase = 0;
            for (TextPosition tp : textPositions) avgBase += tp.getYDirAdj();
            avgBase /= textPositions.size();

            // phát hiện super/sub như cũ:
            StringBuilder sb = new StringBuilder();
            for (TextPosition tp : textPositions) {
                String ch = tp.getUnicode();
                if (ch == null) continue;
                double dy = tp.getYDirAdj() - avgBase;
                if (dy < -2.0) sb.append("_{").append(ch).append("}");
                else if (dy > 2.0) sb.append("^{").append(ch).append("}");
                else sb.append(ch);
            }

            String lineText = sb.toString();
            // đẩy ra writer của PDFTextStripper (để getText vẫn hoạt động)
            super.writeString(lineText, textPositions);
            // đồng thời lưu buffer trang (để endPage ghi một thể)
            pageBuf.append(lineText).append('\n');
            // và lưu “dòng + Y” để chèn ảnh sau
            pageLines.get(pageIndex).add(new Line(lineText, avgBase));
        }
    }

    /* ===================
       Thu ảnh + vị trí Y
       =================== */
    private static class ImageCollector extends PDFStreamEngine {
        final PDDocument doc;
        final List<byte[]> images = new ArrayList<>();
        final Map<Integer, List<ImageToken>> pageImages = new HashMap<>();
        int currentPage = -1;

        static class ImageToken {
            int index;      // index trong images
            float x, y;     // toạ độ gần đúng
            float w, h;
        }

        ImageCollector(PDDocument doc) {
            this.doc = doc;
            // đăng ký operators cần
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new Concatenate());
            addOperator(new SetGraphicsStateParameters());
            addOperator(new SetMatrix());
            addOperator(new DrawObject());
        }

        void collect() throws IOException {
            int i = 0;
            for (PDPage page : doc.getPages()) {
                currentPage = i++;
                pageImages.put(currentPage, new ArrayList<>());
                processPage(page);
            }
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            String opname = operator.getName();
            if ("Do".equals(opname)) {
                COSName objectName = (COSName) operands.get(0);
                PDXObject xobject = getResources().getXObject(objectName);
                if (xobject instanceof PDImageXObject) {
                    PDImageXObject img = (PDImageXObject) xobject;

                    BufferedImage bi = img.getImage();
                    if (bi == null) return;

                    // Lọc ảnh rất nhỏ (tránh bullet/icon)
                    int pixels = bi.getWidth() * bi.getHeight();
                    if (pixels > 0 && pixels < 1200) return; // ~ < 34x34 px

                    // Lọc ảnh gần như trắng
                    if (isNearlyBlank(bi)) return;

                    // Xuất PNG
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ImageIO.write(bi, "png", out);
                    byte[] png = out.toByteArray();

                    // Lấy hộp hiện tại (nếu sau này bạn vẫn muốn dùng)
                    Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                    float x = ctmNew.getTranslateX();
                    float y = ctmNew.getTranslateY();
                    float w = ctmNew.getScalingFactorX();
                    float h = ctmNew.getScalingFactorY();

                    images.add(png);

                    ImageToken tok = new ImageToken();
                    tok.index = images.size(); // 1-based cho {{imageN}}
                    tok.x = x; tok.y = y; tok.w = w; tok.h = h;
                    pageImages.get(currentPage).add(tok);
                }
            } else {
                super.processOperator(operator, operands);
            }
        }
    }

    /* ===============================
       Chèn {{imageN}} theo thứ tự đọc
       =============================== */
    private static String insertImagePlaceholders(String rawByPages, ImageCollector collector, LayoutStripper stripper) {
        String[] pages = rawByPages.split("\f");
        StringBuilder out = new StringBuilder();

        for (int p = 0; p < pages.length; p++) {
            List<LayoutStripper.Line> lines = stripper.pageLines.getOrDefault(p, Collections.emptyList());
            List<ImageCollector.ImageToken> imgs = collector.pageImages.getOrDefault(p, Collections.emptyList());

            if (lines.isEmpty()) {
                // không có dòng – chèn tất cả ảnh đầu/trước rồi text
                String pageText = pages[p].replaceAll("\\f$", "");
                imgs.sort((a,b) -> Float.compare(b.y, a.y)); // y desc
                for (var im : imgs) out.append("{{image").append(im.index).append("}}\n");
                out.append(pageText);
                if (p < pages.length - 1) out.append('\n');
                continue;
            }

            // chuẩn bị buffer dòng → sẽ thêm placeholder sau từng dòng (cuối câu)
            List<StringBuilder> lineBuf = new ArrayList<>(lines.size());
            for (var ln : lines) lineBuf.add(new StringBuilder(ln.text));

            // sort ảnh: y desc (trên→dưới), x asc nếu cùng y
            imgs.sort((a,b) -> {
                int by = Float.compare(b.y, a.y);
                return (by != 0) ? by : Float.compare(a.x, b.x);
            });

            // ánh xạ ảnh → dòng có y thấp hơn “một chút” (ảnh nằm ngay phía trên/đúng dòng)
            // quy tắc đơn giản:
            // - tìm dòng đầu tiên sao cho line.y <= image.y + eps
            // - nếu không có (ảnh cao hơn tất cả dòng header), chèn vào line 0
            final float EPS = 1.5f;
            for (var im : imgs) {
                int target = 0;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).y <= im.y + EPS) { target = i; break; }
                }
                // chèn placeholder ở CUỐI dòng
                lineBuf.get(target).append("\n{{image").append(im.index).append("}}");
            }

            // ghi trang
            for (int i = 0; i < lineBuf.size(); i++) {
                out.append(lineBuf.get(i).toString());
                if (i < lineBuf.size() - 1) out.append('\n');
            }
            if (p < pages.length - 1) out.append('\n');
        }
        return out.toString().replaceAll("\\n{3,}", "\n\n");
    }

    /* ==============================
       Tầng 2b: OCR fallback (Tess4J)
       ============================== */

    private static NativeTextAndImages extractByOCR(PDDocument doc) throws IOException {
        NativeTextAndImages res = new NativeTextAndImages();
        StringBuilder text = new StringBuilder();

        org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
        int pageCount = doc.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            BufferedImage bi = renderer.renderImageWithDPI(i, 300); // 300dpi cho OCR
            // Lưu ảnh trang làm “ảnh nguồn” nếu bạn muốn — nhưng ta không cần.
            // Tách các hình trong trang? OCR không cần, nhưng bạn vẫn có thể detect block ảnh sau này.

            // OCR trang
            String pageText = ocrImage(bi);
            // Chèn ảnh nhúng (nếu có) – ở đây với OCR fallback, ta không có vị trí ảnh, chỉ có thể bỏ qua,
            // hoặc render toàn trang thành 1 ảnh và không chèn placeholder. Thực tế: giữ nguyên text là đủ.
            text.append(pageText == null ? "" : pageText.trim()).append("\n\n");
        }
        res.text = postNormalize(text.toString());
        // res.images: với OCR fallback, có thể để rỗng (hoặc nâng cao: vẫn đi ImageCollector như trên để lấy ảnh nhúng)
        return res;
    }

    // Tess4J (cần thư viện + traineddata: eng, vie nếu cần)
    private static String ocrImage(BufferedImage bi) {
        try {
            net.sourceforge.tess4j.Tesseract t = new net.sourceforge.tess4j.Tesseract();
            // Thêm đường dẫn data nếu cần:
            // t.setDatapath("/path/to/tessdata");
            t.setLanguage("eng+vie"); // bạn có thể đổi theo input
            // một số cấu hình cải chất lượng:
            t.setTessVariable("user_defined_dpi", "300");
            return t.doOCR(bi);
        } catch (Exception e) {
            return "";
        }
    }

    // Ảnh gần như trắng: phần lớn pixel trắng/transparent và độ biến thiên sáng rất thấp
    private static boolean isNearlyBlank(BufferedImage bi) {
        int w = bi.getWidth(), h = bi.getHeight();
        if (w <= 0 || h <= 0) return true;

        // lấy mẫu thưa để chạy nhanh
        int stepX = Math.max(1, w / 32);
        int stepY = Math.max(1, h / 32);

        long opaque = 0, whiteish = 0, n = 0;
        double mean = 0, m2 = 0; // Welford variance on luminance

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int argb = bi.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                if (a < 8) continue; // gần như trong suốt -> bỏ qua
                opaque++;

                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;

                // trắng "nhạt" (>= 246/255)
                if (r >= 246 && g >= 246 && b >= 246) whiteish++;

                // luminance xấp xỉ, double weight cho green
                int lum = (r + g + g + b) >> 2;
                n++;
                double delta = lum - mean;
                mean += delta / n;
                m2 += delta * (lum - mean);
            }
        }
        if (opaque == 0) return true;

        double var = (n > 1) ? m2 / (n - 1) : 0;
        double whiteRatio = whiteish / (double) opaque;

        // >98.5% trắng và phương sai rất thấp -> xem như ảnh trắng
        return whiteRatio > 0.985 && var < 15.0;
    }
}
