    package com.exam.examserver.util;

    import org.docx4j.XmlUtils;
    import org.w3c.dom.*;

    import jakarta.xml.bind.JAXBElement;          // <— dùng jakarta thay cho javax
    import javax.xml.namespace.QName;            // QName vẫn ở javax

    /**
     * Chuyển OMML (Office Math) -> chuỗi "TeX-lite" đủ dùng để import lưu DB.
     * - Tự bọc CTOMath/CTOMathPara thành phần tử root khi marshal DOM (tránh lỗi @XmlRootElement).
     * - Có DEBUG in ra lỗi/chuỗi OMML nếu cần.
     */
    public final class OmmlConverter {

        private OmmlConverter() {}

        public static boolean DEBUG = false;

        public static final String NS_MATH = "http://schemas.openxmlformats.org/officeDocument/2006/math";

        /** OMML JAXB/DOM node -> TeX-lite */
        public static String ommlNodeToText(Object ommlNode) {
            try {
                Document doc = toDom(ommlNode);
                if (doc == null || doc.getDocumentElement() == null) return "";

                String s = walk(doc.getDocumentElement()).trim();
                s = TextNormalize.remapPUA(s);
                s = TextNormalize.normalizePreserveNewlines(s);
                s = TextNormalize.normalizeSuperSubRuns(s);   // <— thêm dòng này

                if (DEBUG) System.out.println("OMML => [" + s + "]");
                return s;
            } catch (Exception e) {
                if (DEBUG) System.out.println("OMML => [ERROR: " + e + "]");
                return "";
            }
        }

        /** Bọc object thành DOM, xử lý cả CTOMath/CTOMathPara không có @XmlRootElement */
        private static Document toDom(Object node) throws Exception {
            if (node == null) return null;

            if (node instanceof Document) return (Document) node;

            if (node instanceof Element) {
                Document d = XmlUtils.neww3cDomDocument();
                d.appendChild(d.importNode((Element) node, true));
                return d;
            }

            if (node instanceof JAXBElement) {
                return XmlUtils.marshaltoW3CDomDocument(node);
            }

            String cn = node.getClass().getName();
            if ("org.docx4j.math.CTOMath".equals(cn)) {
                JAXBElement<?> el = new JAXBElement<>(
                        new QName(NS_MATH, "oMath"),
                        (Class) node.getClass(),
                        node
                );
                return XmlUtils.marshaltoW3CDomDocument(el);
            }
            if ("org.docx4j.math.CTOMathPara".equals(cn)) {
                JAXBElement<?> el = new JAXBElement<>(
                        new QName(NS_MATH, "oMathPara"),
                        (Class) node.getClass(),
                        node
                );
                return XmlUtils.marshaltoW3CDomDocument(el);
            }

            // Thử marshal trực tiếp
            return XmlUtils.marshaltoW3CDomDocument(node);
        }

        /* ================= DOM walker ================= */

        private static String walk(Node n) {
            if (n == null) return "";
            String ln = n.getLocalName() != null ? n.getLocalName() : n.getNodeName();

            switch (ln) {
                /* wrapper */
                case "oMathPara": case "oMath":
                case "m:oMathPara": case "m:oMath":
                    return walkChildren(n);

                /* run & text */
                case "r": case "m:r":
                case "t": case "m:t":
                    return n.getTextContent();

                /* ký tự toán học: <m:chr m:val="…"> */
                case "chr": case "m:chr": {
                    String v = attr(n, NS_MATH, "val");
                    if (v == null || v.isEmpty()) v = attr(n, null, "val");
                    return v != null ? v : "";
                }

                /* ký tự symbol: <m:sym m:char="2228"> (∨) */
                case "sym": case "m:sym": {
                    String hex = attr(n, NS_MATH, "char");
                    if (hex == null || hex.isEmpty()) hex = attr(n, null, "char");
                    try {
                        int cp = Integer.parseInt(hex, 16);
                        return new String(Character.toChars(cp));
                    } catch (Exception ignore) { return ""; }
                }

                /* fraction */
                case "f": case "m:f": {
                    Node num = child(n, "num");
                    Node den = child(n, "den");
                    String a = walkChildren(num).trim();
                    String b = walkChildren(den).trim();
                    return "frac(" + a + "," + b + ")";
                }

                /* radical / sqrt */
                case "rad": case "m:rad": {
                    Node deg = child(n,"deg");
                    Node e   = child(n,"e");
                    String body = walkChildren(e);
                    String d = (deg == null) ? "" : walkChildren(deg).trim();

                    // Nếu không có deg, hoặc deg rỗng/ = "2" => sqrt(...)
                    if (d.isEmpty() || "2".equals(d)) {
                        return "sqrt(" + body + ")";
                    }
                    // Còn lại: root(n)(...)
                    return "root(" + d + ")(" + body + ")";
                }

                /* super/sub */
                case "sSup": case "m:sSup":
                    return walkChildren(child(n,"e")) + "^{" + walkChildren(child(n,"sup")) + "}";
                case "sSub": case "m:sSub":
                    return walkChildren(child(n,"e")) + "_{" + walkChildren(child(n,"sub")) + "}";
                case "sSubSup": case "m:sSubSup":
                    return walkChildren(child(n,"e")) + "_{" + walkChildren(child(n,"sub")) + "}^{"
                            + walkChildren(child(n,"sup")) + "}";

                /* delimiter */
                case "d": case "m:d": {
                    String beg = attr(n, NS_MATH, "begChr");    // có thể null/"" trong một số file
                    String end = attr(n, NS_MATH, "endChr");    // có thể null/""
                    Node eNode  = child(n, "e");
                    String inside = walkChildren(eNode);        // fallback string

                    // eqArr có nghĩa là một "khối nhiều dòng" => ứng viên cho cases
                    Node eqArr = (eNode != null) ? child(eNode, "eqArr") : null;

                    // 1) Nếu là ngoặc nhọn trái (kể cả ký tự “⎧”) HOẶC có eqArr => build cases(...)
                    if (isLeftCurly(beg) || eqArr != null) {
                        java.util.List<String> lines = new java.util.ArrayList<>();
                        if (eqArr != null) {
                            org.w3c.dom.NodeList kids = eqArr.getChildNodes();
                            for (int i = 0; i < kids.getLength(); i++) {
                                Node k = kids.item(i);
                                String ln2 = k.getLocalName();
                                if ("e".equals(ln2)) {           // mỗi <m:e> là một dòng
                                    lines.add(walkChildren(k).trim());
                                }
                            }
                        } else {
                            // Trường hợp hiếm: không có eqArr nhưng vẫn có { … } nhiều dòng
                            for (String ln3 : inside.split("\\R+")) {
                                String s = ln3.trim();
                                if (!s.isEmpty()) lines.add(s);
                            }
                        }
                        return "cases(" + String.join("; ", lines) + ")";
                    }

                    // 2) Nếu là cặp ngoặc nhọn đầy đủ -> giữ nguyên {…}
                    if ("{".equals(beg) && "}".equals(end)) {
                        return "{" + inside + "}";
                    }

                    // 3) Nếu cả hai đều rỗng -> đừng tự ý bọc (), trả nguyên inside
                    if ((beg == null || beg.isEmpty()) && (end == null || end.isEmpty())) {
                        return inside;
                    }

                    // 4) Các delimiter khác: điền mặc định cân đối khi thiếu 1 đầu
                    if (beg == null || beg.isEmpty()) beg = "(";
                    if (end == null || end.isEmpty()) end = ")";
                    return beg + inside + end;
                }

                /* n-ary (sum/prod…) */
                case "nary": case "m:nary": {
                    String op = symbolFromNary(n);
                    String lo = walkChildren(child(n,"sub"));
                    String hi = walkChildren(child(n,"sup"));
                    String e  = walkChildren(child(n,"e"));
                    String low = lo.isBlank() ? "" : "_{" + lo + "}";
                    String up  = hi.isBlank() ? "" : "^{" + hi + "}";
                    return op + low + up + "(" + e + ")";
                }

                /* bar/box/group */
                case "bar": case "m:bar":
                    return "overline(" + walkChildren(child(n,"e")) + ")";
                case "groupChr": case "m:groupChr":
                case "box": case "m:box":
                    return "(" + walkChildren(child(n,"e")) + ")";

                /* limit */
                case "limLow": case "m:limLow":
                    return "lim_{" + walkChildren(child(n,"lim")) + "}(" + walkChildren(child(n,"e")) + ")";
                case "limUpp": case "m:limUpp":
                    return "lim^{" + walkChildren(child(n,"lim")) + "}(" + walkChildren(child(n,"e")) + ")";

                default:
                    return walkChildren(n);
            }
        }

        private static boolean isLeftCurly(String s) {
            return "{".equals(s) || "⎧".equals(s) || "⎨".equals(s) || "⎩".equals(s); // U+23A7..23A9
        }

        private static String walkChildren(Node n) {
            if (n == null) return "";
            StringBuilder sb = new StringBuilder();
            NodeList kids = n.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) sb.append(walk(kids.item(i)));
            return sb.toString();
        }

        private static Node child(Node n, String local) {
            if (n == null) return null;
            NodeList kids = n.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                Node c = kids.item(i);
                String ln = c.getLocalName();
                if (local.equals(ln)) return c;
            }
            return null;
        }

        private static String attr(Node n, String ns, String name) {
            if (!(n instanceof Element)) return null;
            Element e = (Element) n;
            return (ns == null) ? e.getAttribute(name) : e.getAttributeNS(ns, name);
        }

        private static String symbolFromNary(Node n) {
            Node pr = child(n, "naryPr");
            if (pr != null) {
                Node chr = child(pr, "chr");
                if (chr != null) {
                    String v = attr(chr, NS_MATH, "val");
                    if (v != null && !v.isEmpty()) return v;
                }
            }
            return "∑";
        }
    }
