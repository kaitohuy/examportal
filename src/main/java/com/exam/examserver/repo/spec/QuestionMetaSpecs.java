package com.exam.examserver.repo.spec;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;

import com.exam.examserver.model.exam.QuestionMeta;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.enums.*;

public final class QuestionMetaSpecs {
    private QuestionMetaSpecs() {}

    public static Specification<QuestionMeta> bySubjectId(Long subjectId) {
        return (root, cq, cb) -> {
            if (subjectId == null) return cb.conjunction();
            Join<QuestionMeta, Question> q = root.join("question", JoinType.INNER);
            return cb.equal(q.get("subject").get("id"), subjectId);
        };
    }

    public static Specification<QuestionMeta> unitKind(UnitKind kind) {
        return (root, cq, cb) -> (kind == null) ? cb.conjunction() : cb.equal(root.get("unitKind"), kind);
    }

    public static Specification<QuestionMeta> cognitive(CognitiveLevel level) {
        return (root, cq, cb) -> (level == null) ? cb.conjunction() : cb.equal(root.get("cognitiveLevel"), level);
    }

    public static Specification<QuestionMeta> typeCodeIn(Collection<String> codes) {
        return (root, cq, cb) -> {
            if (codes == null || codes.isEmpty()) return cb.conjunction();
            CriteriaBuilder.In<String> in = cb.in(root.get("typeCode"));
            codes.forEach(in::value);
            return in;
        };
    }

    public static Specification<QuestionMeta> chapterIn(Collection<Integer> chapters) {
        return (root, cq, cb) -> {
            if (chapters == null || chapters.isEmpty()) return cb.conjunction();
            CriteriaBuilder.In<Integer> in = cb.in(root.get("chapter"));
            chapters.forEach(in::value);
            return in;
        };
    }

    public static Specification<QuestionMeta> topicId(Long topicId) {
        return (root, cq, cb) -> (topicId == null) ? cb.conjunction() : cb.equal(root.get("topic").get("id"), topicId);
    }

    public static Specification<QuestionMeta> pointsBetween(BigDecimal min, BigDecimal max) {
        return (root, cq, cb) -> {
            if (min == null && max == null) return cb.conjunction();
            if (min != null && max != null) return cb.between(root.get("points"), min, max);
            return (min != null) ? cb.greaterThanOrEqualTo(root.get("points"), min)
                    : cb.lessThanOrEqualTo(root.get("points"), max);
        };
    }

    public static Specification<QuestionMeta> status(RecordStatus status) {
        return (root, cq, cb) -> (status == null) ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<QuestionMeta> notUsedSince(LocalDateTime cutoff) {
        return (root, cq, cb) -> {
            if (cutoff == null) return cb.conjunction();
            // last_used_at is null OR < cutoff
            return cb.or(cb.isNull(root.get("lastUsedAt")),
                    cb.lessThan(root.get("lastUsedAt"), cutoff));
        };
    }

    // Full-text thô: tìm trong Question.content
    public static Specification<QuestionMeta> fullTextContains(String qText) {
        return (root, cq, cb) -> {
            if (qText == null || qText.isBlank()) return cb.conjunction();
            Join<QuestionMeta, Question> q = root.join("question", JoinType.INNER);
            return cb.like(cb.lower(q.get("content")), "%" + qText.toLowerCase() + "%");
        };
    }

    // Lọc theo loại Question (MCQ/ESSAY) nếu cần
    public static Specification<QuestionMeta> questionType(com.exam.examserver.enums.QuestionType type) {
        return (root, cq, cb) -> {
            if (type == null) return cb.conjunction();
            Join<QuestionMeta, Question> q = root.join("question", JoinType.INNER);
            return cb.equal(q.get("questionType"), type);
        };
    }

    public static Specification<QuestionMeta> itemNature(ItemNature n) {
        return (root, q, cb) -> (n == null) ? cb.conjunction()
                : cb.equal(root.get("itemNature"), n);
    }
    public static Specification<QuestionMeta> itemNatureIn(Collection<ItemNature> ns) {
        return (root, q, cb) -> (ns == null || ns.isEmpty()) ? cb.conjunction()
                : root.get("itemNature").in(ns);
    }
}

