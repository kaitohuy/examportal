package com.exam.examserver.repo.spec;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.model.exam.Question;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Set;

public final class QuestionSpecs {
    private QuestionSpecs() {}

    public static Specification<Question> subjectId(Long subjectId) {
        return (root, q, cb) -> cb.equal(root.get("subject").get("id"), subjectId);
    }

    public static Specification<Question> isRootOnly() {
        return (root, q, cb) -> cb.isNull(root.get("parent"));
    }

    public static Specification<Question> hasAnyLabel(Set<QuestionLabel> labels) {
        return (root, query, cb) -> {
            if (labels == null || labels.isEmpty()) return cb.conjunction();
            // join ElementCollection
            Join<Object, Object> jl = root.join("labels", JoinType.LEFT);
            query.distinct(true);
            CriteriaBuilder.In<Object> in = cb.in(jl);
            labels.forEach(in::value);
            return in;
        };
    }

    public static Specification<Question> difficulty(Difficulty d) {
        return (root, q, cb) -> (d == null) ? cb.conjunction() : cb.equal(root.get("difficulty"), d);
    }

    public static Specification<Question> chapter(Integer chapter) {
        return (root, q, cb) -> (chapter == null) ? cb.conjunction() : cb.equal(root.get("chapter"), chapter);
    }

    public static Specification<Question> type(QuestionType type) {
        return (root, q, cb) -> (type == null) ? cb.conjunction() : cb.equal(root.get("questionType"), type);
    }

    public static Specification<Question> createdByContains(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) return cb.conjunction();
            String like = "%" + text.trim().toLowerCase() + "%";
            Join<Object, Object> u = root.join("createdBy", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(u.get("username")), like),
                    cb.like(cb.lower(u.get("fullName")), like)
            );
        };
    }

    public static Specification<Question> createdBetween(LocalDateTime from, LocalDateTime to) {
        return (root, q, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from != null && to != null) return cb.between(root.get("createdAt"), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            return cb.lessThan(root.get("createdAt"), to);
        };
    }

    public static Specification<Question> fullText(String qtext) {
        return (root, q, cb) -> {
            if (qtext == null || qtext.isBlank()) return cb.conjunction();
            String like = "%" + qtext.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("content")), like),
                    cb.like(cb.lower(root.get("answerText")), like),
                    cb.like(cb.lower(root.get("optionA")), like),
                    cb.like(cb.lower(root.get("optionB")), like),
                    cb.like(cb.lower(root.get("optionC")), like),
                    cb.like(cb.lower(root.get("optionD")), like)
            );
        };
    }
}
