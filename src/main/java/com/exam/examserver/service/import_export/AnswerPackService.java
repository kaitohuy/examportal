// src/main/java/com/exam/examserver/service/import_export/AnswerPackService.java
package com.exam.examserver.service.import_export;

import com.exam.examserver.enums.ArchiveVariant;
import com.exam.examserver.model.exam.FileArchive;
import com.exam.examserver.repo.FileArchiveRepository;
import com.exam.examserver.service.QuestionService;
import com.exam.examserver.storage.GcsObjectHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class AnswerPackService {
    private final FileArchiveRepository fileRepo;
    private final ExportQuestionService exportService;
    private final FileArchiveService fileArchiveService;
    private final GcsObjectHelper gcs;
    private final ObjectMapper om = new ObjectMapper();
    private final QuestionService questionService; // dùng khi cần enrich thêm

    public AnswerPackService(FileArchiveRepository fileRepo,
                             ExportQuestionService exportService,
                             FileArchiveService fileArchiveService,
                             GcsObjectHelper gcs,
                             QuestionService questionService) {
        this.fileRepo = fileRepo;
        this.exportService = exportService;
        this.fileArchiveService = fileArchiveService;
        this.gcs = gcs;
        this.questionService = questionService;
    }

    /** Xây gói đáp án từ 1 submission (đã APPROVED); releaseAt có thể null (đặt sau). */
    public FileArchive buildAndSaveAnswerFromSubmission(Long submissionArchiveId,
                                                        Long actorUserId,
                                                        Instant releaseAtOrNull) throws Exception {
        FileArchive sub = fileRepo.findById(submissionArchiveId).orElseThrow();
        if (!"SUBMISSION".equalsIgnoreCase(sub.getKind()))
            throw new IllegalArgumentException("Archive #" + submissionArchiveId + " không phải SUBMISSION");
        if (sub.getVariant() != ArchiveVariant.EXAM)
            throw new IllegalArgumentException("SUBMISSION không phải biến thể EXAM");
        if (sub.getReviewStatus() == null || sub.getReviewStatus().name().equals("PENDING"))
            throw new IllegalStateException("SUBMISSION chưa được duyệt");

        // 1) Đọc ZIP submission từ GCS và tìm blueprint.json
        byte[] subZip = gcs.readBytes(sub.getStorageKey());
        if (subZip == null || subZip.length == 0)
            throw new IllegalStateException("Không đọc được nội dung submission ZIP");

        byte[] blueprintBytes = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(subZip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase().endsWith("blueprint.json")) {
                    blueprintBytes = zis.readAllBytes();
                    break;
                }
            }
        }
        if (blueprintBytes == null)
            throw new IllegalStateException("Thiếu blueprint.json trong ZIP submission — không thể build đáp án.");

        Blueprint bp = om.readValue(blueprintBytes, Blueprint.type());

        // 2) Sinh per-variant DOCX có đáp án
        ByteArrayOutputStream baosZip = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baosZip)) {
            for (int k = 0; k < bp.variants; k++) {
                List<List<Long>> rowsIds = new ArrayList<>();
                for (Blueprint.Row row : bp.rows) {
                    List<Long> ids = (k < row.cells.size() ? row.cells.get(k) : List.of());
                    rowsIds.add(ids == null ? List.of() : ids);
                }
                var header = new ExportQuestionService.ExamHeader(
                        "ĐÁP ÁN", "", "", "", "", "", "", "",
                        "", null, "", ""
                );
                byte[] docx = exportService.exportExamFromBlueprintWithAnswers(rowsIds, header);

                // đặt ngay ở root ZIP
                zos.putNextEntry(new ZipEntry(String.format("De_%02d_DapAn.docx", (k + 1))));
                zos.write(docx);
                zos.closeEntry();
            }
        }
        byte[] answerZip = baosZip.toByteArray();

        // 3) Lưu vào file_archive (APPROVED)
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("variant", "ANSWER");
        meta.put("format", "ZIP_DOCX");
        meta.put("examArchiveId", submissionArchiveId);
        meta.put("variants", bp.variants);
        meta.put("releaseAt", releaseAtOrNull == null ? null : releaseAtOrNull.toString());

        String fname = "Dap_an_Sub#" + submissionArchiveId + ".zip";
        return fileArchiveService.save("EXPORT",
                sub.getSubjectId(),
                actorUserId,          // người thực hiện build (HEAD)
                fname,
                "application/zip",
                answerZip,
                meta);
    }

    /* ====== Blueprint schema tối giản ====== */
    public record Blueprint(int variants, List<Row> rows) {
        public static TypeReference<Blueprint> type() { return new TypeReference<>() {}; }
        public Blueprint { Objects.requireNonNull(rows, "rows"); }
        public static record Row(List<List<Long>> cells) {}
    }
}
