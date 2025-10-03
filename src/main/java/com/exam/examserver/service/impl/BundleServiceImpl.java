// src/main/java/com/exam/examserver/service/bundle/BundleServiceImpl.java
package com.exam.examserver.service.impl;

import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.model.exam.*;
import com.exam.examserver.repo.*;
import com.exam.examserver.service.BundleService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class BundleServiceImpl implements BundleService {

    private final QuestionBundleRepository bundleRepo;
    private final BundleItemRepository itemRepo;
    private final SubjectRepository subjectRepo;
    private final UserRepository userRepo;
    private final EntityManager em;

    public BundleServiceImpl(QuestionBundleRepository bundleRepo,
                             BundleItemRepository itemRepo,
                             SubjectRepository subjectRepo,
                             UserRepository userRepo,
                             EntityManager em) {
        this.bundleRepo = bundleRepo;
        this.itemRepo = itemRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.em = em;
    }

    @Override
    public QuestionBundle create(Long subjectId, Long createdByUserId, String title, String instructions,
                                 List<CreateItem> items, BigDecimal totalPoints, RecordStatus status) {
        QuestionBundle b = new QuestionBundle();
        b.setSubject(subjectRepo.findById(subjectId).orElseThrow());
        b.setCreatedBy(userRepo.findById(createdByUserId).orElse(null));
        b.setTitle(title);
        b.setInstructions(instructions);
        b.setTotalPoints(totalPoints);
        b.setStatus(status == null ? RecordStatus.DRAFT : status);

        // persist bundle first
        b = bundleRepo.save(b);

        if (items != null) {
            for (CreateItem it : items) {
                BundleItem bi = new BundleItem();
                bi.setBundle(b);
                bi.setQuestion(em.getReference(Question.class, it.questionId()));
                bi.setOrderIndex(it.orderIndex());
                bi.setPointsOverride(it.pointsOverride());
                bi.setNote(it.note());
                itemRepo.save(bi);
            }
        }
        return b;
    }

    @Override
    public Map<Long, String> findInstructionsByQuestionIds(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return java.util.Collections.emptyMap();
        return itemRepo.findStemsByQuestionIds(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuestionBundleStemView::getQuestionId,
                        QuestionBundleStemView::getInstructions,
                        (a, b) -> a   // nếu 1 câu nằm trong nhiều bundle: giữ cái đầu tiên
                ));
    }
}
