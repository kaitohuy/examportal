package com.exam.examserver.repo.spec;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.IssueStatus;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.QuestionIssue;
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
        return (root, query, cb) -> {
            if (qtext == null || qtext.isBlank()) return cb.conjunction();

            String raw = qtext.trim().toLowerCase();
            String like = "%" + raw + "%";

            // --- tách baseCode nếu chuỗi có dạng c1.NH5.10b) ---
            // vd: raw = "c1.nh5.10b)"  -> base = "nh5.10b)"
            String base = raw.replaceFirst("^c\\d+\\.", "");
            String baseLike = "%" + base + "%";

            // Các field text
            Expression<String> content = cb.lower(root.get("content"));
            Expression<String> ansText = cb.lower(root.get("answerText"));
            Expression<String> a = cb.lower(root.get("optionA"));
            Expression<String> b = cb.lower(root.get("optionB"));
            Expression<String> c = cb.lower(root.get("optionC"));
            Expression<String> d = cb.lower(root.get("optionD"));

            // questionCode
            Expression<String> code = cb.lower(root.get("questionCode"));

            // id cast về string (để tìm theo id)
            Expression<String> idExpr = cb.toString(root.get("id"));

            Predicate codePred;
            if (!base.equals(raw)) {
                codePred = cb.or(
                        cb.like(code, like),      // "c1.nh5.10b)"
                        cb.like(code, baseLike)   // "nh5.10b)"  (parent)
                );
            } else {
                codePred = cb.like(code, like);
            }

            return cb.or(
                    cb.like(content, like),
                    cb.like(ansText, like),
                    cb.like(a, like),
                    cb.like(b, like),
                    cb.like(c, like),
                    cb.like(d, like),
                    codePred,
                    cb.like(idExpr, like)        // tìm theo id "352"
            );
        };
    }

    // NEW: trả về cb.conjunction() khi flagged == null
    public static Specification<Question> flagged(Boolean flagged) {
        if (flagged == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> {
            assert query != null;
            Subquery<Long> sub = query.subquery(Long.class);
            Root<QuestionIssue> qi = sub.from(QuestionIssue.class);
            sub.select(cb.literal(1L))
                    .where(
                            cb.equal(qi.get("question").get("id"), root.get("id")),
                            cb.equal(qi.get("status"), IssueStatus.OPEN)
                    );
            return flagged ? cb.exists(sub) : cb.not(cb.exists(sub));
        };
    }

    /** NEW: lọc các câu chưa bị đưa vào thùng rác */
    public static Specification<Question> notDeleted() {
        return (root, q, cb) -> cb.isFalse(root.get("isDeleted"));
    }
    public static Specification<Question> deletedOnly() {
        return (root, q, cb) -> cb.isTrue(root.get("isDeleted"));
    }
}
