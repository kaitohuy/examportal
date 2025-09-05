package com.exam.examserver.service.import_export;

import com.exam.examserver.dto.importing.ExtractResult;
import com.exam.examserver.util.OmmlConverter;
import com.exam.examserver.util.TextNormalize;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.docx4j.XmlUtils;
import org.docx4j.dml.Graphic;
import org.docx4j.dml.GraphicData;
import org.docx4j.dml.picture.Pic;
import org.docx4j.dml.wordprocessingDrawing.Anchor;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.model.listnumbering.Emulator;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.*;

import jakarta.xml.bind.JAXBElement;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.poi.hwmf.usermodel.HwmfPicture;
import org.apache.poi.hemf.usermodel.HemfPicture;

/** Đọc DOCX giữ đúng thứ tự text / OMML / image; render numbering (1., A., i., …); gắn {hl} cho run nhấn mạnh. */
public class DocxOmmlExtractor {

    private static final String NS_VML  = "urn:schemas-microsoft-com:vml";
    private static final String NS_REL  = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_MATH = "http://schemas.openxmlformats.org/officeDocument/2006/math";


    private static class NumberingState { Map<String, int[]> counters = new HashMap<>(); }

    public static ExtractResult extractWord(InputStream is) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(is);
        MainDocumentPart mdp = pkg.getMainDocumentPart();

        StringBuilder sb = new StringBuilder();
        List<byte[]> images = new ArrayList<>();
        NumberingState numState = new NumberingState();

        for (Object block : mdp.getContent()) {
            Object unwrapped = XmlUtils.unwrap(block);

            if (unwrapped instanceof P) {
                appendParagraph((P) unwrapped, sb, images, pkg, numState);
            } else if (unwrapped instanceof Tbl) {
                Tbl tbl = (Tbl) unwrapped;
                for (Object ro : tbl.getContent()) {
                    Tr row = (Tr) XmlUtils.unwrap(ro);
                    for (Object co : row.getContent()) {
                        Tc cell = (Tc) XmlUtils.unwrap(co);
                        for (Object po : cell.getContent()) {
                            Object p = XmlUtils.unwrap(po);
                            if (p instanceof P) appendParagraph((P) p, sb, images, pkg, numState);
                        }
                    }
                }
            }
        }
        return new ExtractResult(sb.toString(), images);
    }

    public static ExtractResult extractPdf(InputStream is) throws IOException {
        List<byte[]> images = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        try (PDDocument pdf = PDDocument.load(is)) {
            int pages = pdf.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                sb.append(stripper.getText(pdf)).append('\n');

                PDPage page = pdf.getPage(p - 1);
                PDResources res = page.getResources();
                if (res != null) {
                    for (COSName name : res.getXObjectNames()) {
                        PDXObject xobj = res.getXObject(name);
                        if (xobj instanceof PDImageXObject) {
                            PDImageXObject img = (PDImageXObject) xobj;
                            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                                ImageIO.write(img.getImage(), "png", bos);
                                images.add(bos.toByteArray());
                                sb.append("{{image").append(images.size()).append("}}").append('\n');
                            }
                        }
                    }
                }
            }
        }
        return new ExtractResult(sb.toString(), images);
    }

    private static void appendParagraph(P p, StringBuilder sb, List<byte[]> images,
                                        WordprocessingMLPackage pkg, NumberingState numState) {
        // 1) numbering
        String numberingText = getNumberingTextByEmulator(p, pkg);
        if (numberingText == null) numberingText = fallbackNumbering(p, pkg, numState);
        if (numberingText != null && !numberingText.isBlank()) sb.append(numberingText).append(" ");

        // 2) nội dung paragraph
        traverseNode(p, sb, images, pkg.getMainDocumentPart());
        sb.append('\n');
    }

    private static String getNumberingTextByEmulator(P p, WordprocessingMLPackage pkg) {
        try {
            PPr ppr = p.getPPr();
            if (ppr == null || ppr.getNumPr() == null) return null;

            String pStyleVal = (ppr.getPStyle() != null && ppr.getPStyle().getVal() != null)
                    ? ppr.getPStyle().getVal() : null;

            String numId = (ppr.getNumPr().getNumId() != null && ppr.getNumPr().getNumId().getVal() != null)
                    ? ppr.getNumPr().getNumId().getVal().toString() : null;

            String ilvl = "0";
            if (ppr.getNumPr().getIlvl() != null && ppr.getNumPr().getIlvl().getVal() != null) {
                ilvl = ppr.getNumPr().getIlvl().getVal().toString();
            }
            if (numId == null) return null;

            Emulator.ResultTriple triple = Emulator.getNumber(pkg, pStyleVal, numId, ilvl);
            if (triple == null) return null;

            if (triple.getBullet() != null && !triple.getBullet().isBlank()) return triple.getBullet().trim();
            if (triple.getNumString() != null && !triple.getNumString().isBlank()) return triple.getNumString().trim();
            return null;
        } catch (Exception e) { return null; }
    }

    private static String fallbackNumbering(P p, WordprocessingMLPackage pkg, NumberingState st) {
        try {
            PPr ppr = p.getPPr();
            if (ppr == null || ppr.getNumPr() == null) return null;

            BigInteger numIdBI = (ppr.getNumPr().getNumId() != null) ? ppr.getNumPr().getNumId().getVal() : null;
            BigInteger ilvlBI  = (ppr.getNumPr().getIlvl()  != null) ? ppr.getNumPr().getIlvl().getVal()  : BigInteger.ZERO;
            if (numIdBI == null) return null;
            int ilvl = ilvlBI.intValue();

            NumberingDefinitionsPart ndp = pkg.getMainDocumentPart().getNumberingDefinitionsPart();
            if (ndp == null) return null;
            org.docx4j.wml.Numbering numbering = ndp.getJaxbElement();
            if (numbering == null) return null;

            org.docx4j.wml.Numbering.Num num = null;
            for (org.docx4j.wml.Numbering.Num n : numbering.getNum()) {
                if (n.getNumId() != null && n.getNumId().equals(numIdBI)) { num = n; break; }
            }
            if (num == null || num.getAbstractNumId() == null || num.getAbstractNumId().getVal() == null) return null;

            BigInteger absId = num.getAbstractNumId().getVal();

            org.docx4j.wml.Numbering.AbstractNum abs = null;
            for (org.docx4j.wml.Numbering.AbstractNum an : numbering.getAbstractNum()) {
                if (an.getAbstractNumId() != null && an.getAbstractNumId().equals(absId)) { abs = an; break; }
            }
            if (abs == null) return null;

            Lvl lvlDef = null;
            for (Lvl l : abs.getLvl()) {
                if (l.getIlvl() != null && l.getIlvl().intValue() == ilvl) { lvlDef = l; break; }
            }
            if (lvlDef == null) return null;

            String key = numIdBI.toString();
            int[] ctrs = st.counters.computeIfAbsent(key, k -> new int[9]);

            int start = 1;
            if (lvlDef.getStart() != null && lvlDef.getStart().getVal() != null) start = lvlDef.getStart().getVal().intValue();

            if (ctrs[ilvl] == 0) {
                ctrs[ilvl] = start;
                for (int j = ilvl + 1; j < ctrs.length; j++) ctrs[j] = 0;
            } else {
                ctrs[ilvl] += 1;
                for (int j = ilvl + 1; j < ctrs.length; j++) ctrs[j] = 0; // <-- có điều kiện dừng
            }

            String fmt = null;
            if (lvlDef.getNumFmt() != null && lvlDef.getNumFmt().getVal() != null) {
                try { fmt = lvlDef.getNumFmt().getVal().value(); }
                catch (Throwable ignore) { fmt = String.valueOf(lvlDef.getNumFmt().getVal()); }
            }
            String numStr = formatByNumFmt(ctrs[ilvl], fmt);

            String pattern = "%1.";
            if (lvlDef.getLvlText() != null && lvlDef.getLvlText().getVal() != null) pattern = lvlDef.getLvlText().getVal();
            String label = pattern.replace("%1", numStr);
            for (int k = 2; k <= 9; k++) label = label.replace("%" + k, String.valueOf(ctrs[ilvl]));

            return label;
        } catch (Exception e) { return null; }
    }

    private static String formatByNumFmt(int n, String fmt) {
        if (fmt == null) fmt = "decimal";
        switch (fmt) {
            case "decimal":      return n + ".";
            case "lowerLetter":  return toAlpha(n, false) + ".";
            case "upperLetter":  return toAlpha(n, true)  + ".";
            case "lowerRoman":   return toRoman(n).toLowerCase() + ".";
            case "upperRoman":   return toRoman(n).toUpperCase() + ".";
            case "bullet":       return "•";
            default:             return n + ".";
        }
    }

    private static String toAlpha(int n, boolean upper) {
        StringBuilder sb = new StringBuilder();
        while (n > 0) { n--; sb.append((char) ('a' + (n % 26))); n /= 26; }
        String s = sb.reverse().toString();
        return upper ? s.toUpperCase() : s;
    }

    private static String toRoman(int num) {
        int[]    v   = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] sym = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder r = new StringBuilder();
        for (int i=0;i<v.length;i++) while (num>=v[i]) { num-=v[i]; r.append(sym[i]); }
        return r.toString();
    }

    /** Duyệt đệ quy: Text, OMML, Drawing (DML), VML/OLE, gắn {hl} cho run nhấn mạnh */
    private static void traverseNode(Object node, StringBuilder sb, List<byte[]> images, MainDocumentPart mdp) {
        Object u = XmlUtils.unwrap(node);

        // Run (w:r): xét nhấn mạnh & wrap {hl}
        if (u instanceof R) {
            R r = (R) u;
            RPr rpr = r.getRPr();

            boolean bold = isOn(rpr != null ? rpr.getB() : null) || isOn(rpr != null ? rpr.getBCs() : null);

            boolean colored = false;
            if (rpr != null && rpr.getColor() != null && rpr.getColor().getVal() != null) {
                String v = rpr.getColor().getVal();
                colored = v != null && !v.isBlank() && !"auto".equalsIgnoreCase(v);
            }

            boolean highlighted = false;
            if (rpr != null && rpr.getHighlight() != null) {
                Object v = rpr.getHighlight().getVal(); // String hoặc enum -> toString()
                highlighted = v != null && !"none".equalsIgnoreCase(v.toString());
            }

            boolean emph = bold || colored || highlighted;

            String va = null;
            if (rpr != null && rpr.getVertAlign() != null && rpr.getVertAlign().getVal() != null) {
                va = rpr.getVertAlign().getVal().value(); // "superscript" | "subscript" | "baseline"
            }
            boolean isSup = "superscript".equalsIgnoreCase(va);
            boolean isSub = "subscript".equalsIgnoreCase(va);

            // render nội dung run vào buffer tạm
            StringBuilder tmp = new StringBuilder();
            for (Object child : childrenOf(r)) traverseNode(child, tmp, images, mdp);

            String text = tmp.toString();
            // >>> NEW: phát hiện sub/sup ở rPr.vertAlign <<<
            if (rpr != null && rpr.getVertAlign() != null && rpr.getVertAlign().getVal() != null) {
                String vagn = rpr.getVertAlign().getVal().value(); // "baseline" | "subscript" | "superscript"
                if ("subscript".equalsIgnoreCase(vagn))      text = "_{" + text + "}";
                else if ("superscript".equalsIgnoreCase(vagn)) text = "^{" + text + "}";
            }

            if (!text.isEmpty()) {
                if (emph) sb.append("{hl}").append(text).append("{/hl}");
                else sb.append(text);
            }
            return;
        }

        // Text
        if (u instanceof Text) {
            String v = TextNormalize.normalizeHard(((Text) u).getValue());
            v = TextNormalize.normalizeSuperSubRuns(v);     // <— thêm dòng này
            if (v != null) sb.append(v);
            return;
        }

        // DML image
        if (u instanceof Drawing) {
            Drawing dr = (Drawing) u;
            for (Object aoi : dr.getAnchorOrInline()) {
                Object any = XmlUtils.unwrap(aoi);
                if (any instanceof Inline)      handleGraphic(((Inline) any).getGraphic(), sb, images, mdp);
                else if (any instanceof Anchor) handleGraphic(((Anchor) any).getGraphic(), sb, images, mdp);
            }
            return;
        }

        if (u instanceof Br) {        // <w:br/>, <w:cr/>
            sb.append('\n');
            return;
        }
        if (u instanceof R.Tab) {                          // <w:tab/>
            sb.append(' ');
            return;
        }

        if (u.getClass().getName().endsWith(".R$Tab")) {
            sb.append(' ');
            return;
        }

        // VML/OLE fallback images
        if (hasVmlImage(u)) extractVmlImages(u, sb, images, mdp);

        // OMML at any depth
        if (isOmmlStandalone(u)) {
            String math = OmmlConverter.ommlNodeToText(u);
            if (math == null || math.isBlank()) math = collectAllMathTokens(u);
            if (math != null && !math.isBlank()) sb.append(math);
        }

        // Containers
        for (Object k : childrenOf(u)) traverseNode(k, sb, images, mdp);
    }

    /* ===== images ===== */

    private static void handleGraphic(Graphic graphic, StringBuilder sb, List<byte[]> images, MainDocumentPart mdp) {
        if (graphic == null) return;
        GraphicData gd = graphic.getGraphicData();
        for (Object any : gd.getAny()) {
            Object u = XmlUtils.unwrap(any);
            if (u instanceof Pic) {
                Pic pic = (Pic) u;
                String rId = pic.getBlipFill().getBlip().getEmbed();
                try {
                    Part part = mdp.getRelationshipsPart().getPart(rId);
                    if (part instanceof BinaryPartAbstractImage) writeImagePartAsPngPlaceholder((BinaryPartAbstractImage) part, sb, images);
                } catch (Exception ignore) { }
            }
        }
    }

    private static boolean hasVmlImage(Object node) {
        try {
            Document d = XmlUtils.marshaltoW3CDomDocument(node);
            NodeList nl = d.getElementsByTagNameNS(NS_VML, "imagedata");
            return nl != null && nl.getLength() > 0;
        } catch (Exception e) { return false; }
    }

    private static void extractVmlImages(Object node, StringBuilder sb, List<byte[]> images, MainDocumentPart mdp) {
        try {
            Document d = XmlUtils.marshaltoW3CDomDocument(node);
            NodeList nl = d.getElementsByTagNameNS(NS_VML, "imagedata");
            for (int i = 0; i < nl.getLength(); i++) {
                Element el = (Element) nl.item(i);
                String rId = null;
                if (el.hasAttributeNS(NS_REL, "id")) rId = el.getAttributeNS(NS_REL, "id");
                if (rId == null || rId.isBlank()) rId = el.getAttribute("r:id");
                if (rId == null || rId.isBlank()) continue;

                try {
                    Part part = mdp.getRelationshipsPart().getPart(rId);
                    if (part instanceof BinaryPartAbstractImage) writeImagePartAsPngPlaceholder((BinaryPartAbstractImage) part, sb, images);
                } catch (Exception ignore) { }
            }
        } catch (Exception ignore) { }
    }

    private static void writeImagePartAsPngPlaceholder(BinaryPartAbstractImage part, StringBuilder sb, List<byte[]> images) {
        try {
            String name = part.getPartName().getName().toLowerCase(Locale.ROOT);
            byte[] raw = part.getBytes();
            byte[] png;

            if (name.endsWith(".png")) {
                png = raw;
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                try (ByteArrayInputStream in = new ByteArrayInputStream(raw);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    BufferedImage bi = ImageIO.read(in);
                    if (bi == null) return;
                    ImageIO.write(bi, "png", out);
                    png = out.toByteArray();
                }
            } else if (name.endsWith(".wmf")) {
                png = convertWmfToPng(raw);
                if (png == null) return;
            } else if (name.endsWith(".emf")) {
                png = convertEmfToPng(raw);
                if (png == null) return;
            } else {
                try (ByteArrayInputStream in = new ByteArrayInputStream(raw);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    BufferedImage bi = ImageIO.read(in);
                    if (bi == null) return;
                    ImageIO.write(bi, "png", out);
                    png = out.toByteArray();
                }
            }

            images.add(png);
            sb.append("{{image").append(images.size()).append("}}");
        } catch (Exception ignore) { }
    }

    private static byte[] convertWmfToPng(byte[] raw) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(raw);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            HwmfPicture pic = new HwmfPicture(in);
            Rectangle2D b = pic.getBounds();
            int w = Math.max(1, (int) Math.ceil(b.getWidth()));
            int h = Math.max(1, (int) Math.ceil(b.getHeight()));
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            pic.draw(g, new Rectangle2D.Double(0, 0, w, h));
            g.dispose();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) { return null; }
    }

    private static byte[] convertEmfToPng(byte[] raw) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(raw);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            HemfPicture pic = new HemfPicture(in);
            Rectangle2D b = pic.getBoundsInPoints();
            int w = Math.max(1, (int) Math.ceil(b.getWidth()));
            int h = Math.max(1, (int) Math.ceil(b.getHeight()));
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            pic.draw(g, new Rectangle2D.Double(0, 0, w, h));
            g.dispose();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) { return null; }
    }

    /* ===== misc ===== */

    private static boolean isOmmlStandalone(Object o) {
        if (o instanceof JAXBElement) {
            JAXBElement<?> el = (JAXBElement<?>) o;
            String ns = el.getName() != null ? el.getName().getNamespaceURI() : "";
            return NS_MATH.equals(ns);
        }
        return o.getClass().getName().startsWith("org.docx4j.math");
    }

    private static String collectAllMathTokens(Object omml) {
        try {
            Document d = XmlUtils.marshaltoW3CDomDocument(omml);
            NodeList ts = d.getElementsByTagNameNS(NS_MATH, "t");
            if (ts == null || ts.getLength() == 0) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ts.getLength(); i++) sb.append(ts.item(i).getTextContent());
            String s = sb.toString();
            s = TextNormalize.remapPUA(s);
            s = TextNormalize.normalizePreserveNewlines(s);
            s = TextNormalize.normalizeSuperSubRuns(s);
            return s;
        } catch (Exception e) { return null; }
    }

    private static List<Object> childrenOf(Object u) {
        if (u == null) return Collections.emptyList();

        if (u instanceof JAXBElement) {
            Object val = ((JAXBElement<?>) u).getValue();
            return val != null ? Collections.singletonList(val) : Collections.emptyList();
        }
        if (u instanceof ContentAccessor) {
            return ((ContentAccessor) u).getContent();
        }
        if (u instanceof GraphicData) {
            return new ArrayList<>(((GraphicData) u).getAny());
        }
        return Collections.emptyList();
    }

    // bold trong docx: b != null và (val==null || val==true) — với docx4j hiện tại isVal() trả boolean
    private static boolean isOn(BooleanDefaultTrue b) {
        return b != null && b.isVal();
    }
}
