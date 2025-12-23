// src/main/java/com/exam/examserver/service/impl/QuestionMetaServiceImpl.java
package com.exam.examserver.service.impl;

import com.exam.examserver.enums.CognitiveLevel;
import com.exam.examserver.enums.ItemNature;
import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.enums.UnitKind;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.QuestionMeta;
import com.exam.examserver.model.exam.Topic;
import com.exam.examserver.repo.QuestionMetaRepository;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.repo.TopicRepository;
import com.exam.examserver.service.QuestionMetaService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class QuestionMetaServiceImpl implements QuestionMetaService {

    private final QuestionMetaRepository metaRepo;
    private final TopicRepository topicRepo;
    private final QuestionRepository questionRepo; // thêm để lấy reference nhanh

    public QuestionMetaServiceImpl(QuestionMetaRepository metaRepo,
                                   TopicRepository topicRepo,
                                   QuestionRepository questionRepo) {
        this.metaRepo = metaRepo;
        this.topicRepo = topicRepo;
        this.questionRepo = questionRepo;
    }

    @Override
    public QuestionMeta upsertDefault(Long questionId, UnitKind unitKind, BigDecimal points,
                                      Integer chapter, Long topicId, RecordStatus status,
                                      CognitiveLevel cognitiveLevel, String typeCode, String problemType) {
        // gọi sang hàm mới với UNKNOWN để không phá chỗ gọi cũ
        return upsertDefault(questionId, unitKind, points, chapter, topicId, status, cognitiveLevel, typeCode, ItemNature.UNKNOWN, problemType);
    }

    @Override
    public QuestionMeta upsertDefault(Long questionId, UnitKind unitKind, BigDecimal points,
                                      Integer chapter, Long topicId, RecordStatus status,
                                      CognitiveLevel cognitiveLevel, String typeCode,
                                      ItemNature itemNature, String problemType) {

        QuestionMeta m = metaRepo.findById(questionId).orElseGet(() -> {
            Question qref = questionRepo.getReferenceById(questionId);
            QuestionMeta x = new QuestionMeta();
            x.setQuestion(qref);                            // @MapsId
            x.setUnitKind(UnitKind.FULL_QUESTION);
            x.setPoints(new BigDecimal("1.00"));
            x.setStatus(RecordStatus.DRAFT);
            x.setItemNature(ItemNature.UNKNOWN);
            return x;
        });

        if (unitKind != null)        m.setUnitKind(unitKind);
        if (points != null)          m.setPoints(points);
        if (chapter != null)
            m.setChapter(chapter);

        if (topicId != null) {
            Topic t = topicRepo.findById(topicId).orElse(null);
            m.setTopic(t);
        }

        if (status != null)          m.setStatus(status);
        m.setCognitiveLevel(cognitiveLevel);

        if (typeCode != null && !typeCode.isBlank())
            m.setTypeCode(typeCode);

        if (itemNature != null)
            m.setItemNature(itemNature);

        if (problemType != null)
            m.setProblemType(problemType);

        QuestionMeta saved = metaRepo.saveAndFlush(m);
        System.out.printf("[META] upsert OK: qId=%d, unit=%s, points=%s, nature=%s%n",
                saved.getQuestionId(), saved.getUnitKind(), saved.getPoints(), saved.getItemNature());
        return saved;
    }

    @Override
    public void markUsed(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long qid : questionIds) {
            metaRepo.findById(qid).ifPresent(m -> {
                m.setLastUsedAt(now);
                m.setUsageCount((m.getUsageCount() == null ? 0 : m.getUsageCount()) + 1);
                metaRepo.save(m);
            });
        }
    }

    @Override
    public List<String> findDistinctTypeCodesApproved(Long subjectId) {
        // 1. Lấy danh sách gốc từ DB (VD: [2.1.1, 2.1.2, 3.1])
        List<String> rawCodes = metaRepo.findDistinctTypeCodesBySubjectApproved(subjectId);

        // 2. Dùng TreeSet để sắp xếp và loại bỏ trùng lặp
        Set<String> distinct = new TreeSet<>();

        for (String code : rawCodes) {
            if (code == null || code.isBlank()) continue;

            // Thêm mã gốc
            distinct.add(code);

            // Tách theo dấu chấm để tạo các mã cha
            // VD code="2.1.1" -> parts=["2", "1", "1"]
            String[] parts = code.split("\\.");
            if (parts.length > 1) {
                // Thêm cấp 1 (VD: "2")
                StringBuilder parent = new StringBuilder(parts[0]);
                distinct.add(parent.toString());

                // Thêm các cấp tiếp theo (trừ cấp cuối cùng vì đã add ở trên)
                // VD: vòng lặp chạy i=1 -> thêm "2.1"
                for (int i = 1; i < parts.length - 1; i++) {
                    parent.append(".").append(parts[i]);
                    distinct.add(parent.toString());
                }
            }
        }

        // Kết quả trả về: [2, 2.1, 2.1.1, 2.1.2, 3, 3.1]
        System.out.println("arrays: " + distinct.toString());
        return new ArrayList<>(distinct);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getProblemTypesBySubject(Long subjectId) {
        return metaRepo.findDistinctProblemTypesBySubjectApproved(subjectId);
    }

}
