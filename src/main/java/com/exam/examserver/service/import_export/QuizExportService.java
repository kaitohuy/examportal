package com.exam.examserver.service.import_export;

import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.service.QuizService;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

@Service
public class QuizExportService {

    private final QuizService quizService;
    private static final String FONT_PATH = "src/main/resources/fonts/OpenSans.ttf";

    public QuizExportService(QuizService quizService) {
        this.quizService = quizService;
    }

    public byte[] exportQuizToPdf(Long quizId, boolean includeAnswers) throws IOException {
        if (quizId == null) throw new IllegalArgumentException("Quiz ID cannot be null");

        List<QuestionDTO> questions = quizService.getQuestionsByQuiz(quizId, !includeAnswers);
        if (questions == null || questions.isEmpty()) {
            throw new IllegalStateException("No questions found for quiz ID: " + quizId);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            // Giữ nguyên theo yêu cầu
            PdfFont font = PdfFontFactory.createFont(FONT_PATH);
            document.setFont(font);

            int cnt = 0;
            for (QuestionDTO q : questions) {
                document.add(new Paragraph("Question " + (++cnt)).setBold().setFontSize(14));
                document.add(new Paragraph("Content: " + q.getContent()));

                // ẢNH: list -> cover
                if (q.getImages() != null && !q.getImages().isEmpty()) {
                    for (var imgDto : q.getImages()) {
                        try {
                            Image img = new Image(ImageDataFactory.create(new URL(imgDto.getUrl())));
                            img.scaleToFit(400, 300);
                            document.add(img);
                        } catch (Exception e) {
                            document.add(new Paragraph("Error loading image: " + imgDto.getUrl()));
                        }
                    }
                } else if (q.getImageUrl() != null) {
                    try {
                        Image img = new Image(ImageDataFactory.create(new URL(q.getImageUrl())));
                        img.scaleToFit(400, 300);
                        document.add(img);
                    } catch (Exception e) {
                        document.add(new Paragraph("Error loading image: " + q.getImageUrl()));
                    }
                }

                if (q.getOptionA() != null) document.add(new Paragraph("A. " + q.getOptionA()));
                if (q.getOptionB() != null) document.add(new Paragraph("B. " + q.getOptionB()));
                if (q.getOptionC() != null) document.add(new Paragraph("C. " + q.getOptionC()));
                if (q.getOptionD() != null) document.add(new Paragraph("D. " + q.getOptionD()));

                if (includeAnswers) {
                    if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE && q.getAnswer() != null) {
                        document.add(new Paragraph("Answer: " + q.getAnswer()));
                    } else if (q.getQuestionType() == QuestionType.ESSAY && q.getAnswerText() != null) {
                        document.add(new Paragraph("Answer: " + q.getAnswerText()));
                    }
                }

                document.add(new Paragraph("\n"));
            }

            document.close();
            return baos.toByteArray();
        }
    }

    public byte[] exportQuizToWord(Long quizId, boolean includeAnswers) throws IOException {
        if (quizId == null) throw new IllegalArgumentException("Quiz ID cannot be null");

        List<QuestionDTO> questions = quizService.getQuestionsByQuiz(quizId, !includeAnswers);
        if (questions == null || questions.isEmpty()) {
            throw new IllegalStateException("No questions found for quiz ID: " + quizId);
        }

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            int cnt = 0;
            for (QuestionDTO q : questions) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setBold(true);
                run.setFontSize(14);
                run.setText("Question " + (++cnt));

                paragraph = document.createParagraph();
                run = paragraph.createRun();
                run.setText("Content: " + q.getContent());

                // ẢNH: list -> cover (ép PNG để tránh WebP)
                if (q.getImages() != null && !q.getImages().isEmpty()) {
                    for (var imgDto : q.getImages()) {
                        String safeUrl = forcePng(imgDto.getUrl());
                        try (var is = new URL(safeUrl).openStream()) {
                            XWPFParagraph ip = document.createParagraph();
                            XWPFRun ir = ip.createRun();
                            ir.addPicture(is, XWPFDocument.PICTURE_TYPE_PNG, "image",
                                    Units.toEMU(400), Units.toEMU(300));
                        } catch (Exception e) {
                            XWPFParagraph ep = document.createParagraph();
                            ep.createRun().setText("Error loading image: " + imgDto.getUrl());
                        }
                    }
                } else if (q.getImageUrl() != null) {
                    String safeUrl = forcePng(q.getImageUrl());
                    try (var is = new URL(safeUrl).openStream()) {
                        XWPFParagraph ip = document.createParagraph();
                        XWPFRun ir = ip.createRun();
                        ir.addPicture(is, XWPFDocument.PICTURE_TYPE_PNG, "image",
                                Units.toEMU(400), Units.toEMU(300));
                    } catch (Exception e) {
                        XWPFParagraph ep = document.createParagraph();
                        ep.createRun().setText("Error loading image: " + q.getImageUrl());
                    }
                }

                if (q.getOptionA() != null) { paragraph = document.createParagraph(); paragraph.createRun().setText("A. " + q.getOptionA()); }
                if (q.getOptionB() != null) { paragraph = document.createParagraph(); paragraph.createRun().setText("B. " + q.getOptionB()); }
                if (q.getOptionC() != null) { paragraph = document.createParagraph(); paragraph.createRun().setText("C. " + q.getOptionC()); }
                if (q.getOptionD() != null) { paragraph = document.createParagraph(); paragraph.createRun().setText("D. " + q.getOptionD()); }

                if (includeAnswers) {
                    if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE && q.getAnswer() != null) {
                        paragraph = document.createParagraph();
                        paragraph.createRun().setText("Answer: " + q.getAnswer());
                    } else if (q.getQuestionType() == QuestionType.ESSAY && q.getAnswerText() != null) {
                        paragraph = document.createParagraph();
                        paragraph.createRun().setText("Answer: " + q.getAnswerText());
                    }
                }

                paragraph = document.createParagraph();
                paragraph.createRun().addBreak();
            }

            document.write(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Biến Cloudinary URL bất kỳ thành PNG (thêm 'f_png' sau '/upload/').
     * Nếu không phải Cloudinary, trả về nguyên URL.
     */
    private String forcePng(String url) {
        if (url == null) return null;
        int i = url.indexOf("/upload/");
        if (i < 0) return url;
        return url.substring(0, i + 8) + "f_png/" + url.substring(i + 8);
    }
}
