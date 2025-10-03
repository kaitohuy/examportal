package com.exam.examserver.repo.spec;

import com.exam.examserver.enums.ExamTaskStatus;
import com.exam.examserver.model.exam.ExamTask;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;

import java.util.ArrayList;
import java.util.List;

public final class ExamTaskSpecs {
    private ExamTaskSpecs() {}

    public static Specification<ExamTask> scope(Long assigneeId, Long createdByHeadId) {
        return (root, q, cb) -> {
            List<Predicate> ors = new ArrayList<>();
            if (assigneeId != null)      ors.add(cb.equal(root.get("assignedToId"), assigneeId));
            if (createdByHeadId != null) ors.add(cb.equal(root.get("createdByHeadId"), createdByHeadId));
            if (ors.isEmpty()) return cb.conjunction();
            return cb.or(ors.toArray(new Predicate[0])); // <-- hết mơ hồ
        };
    }

    public static Specification<ExamTask> bySubject(Long subjectId) {
        return (root, q, cb) ->
                subjectId == null ? cb.conjunction() : cb.equal(root.get("subjectId"), subjectId);
    }

    public static Specification<ExamTask> byStatus(ExamTaskStatus status) {
        return (root, q, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<ExamTask> createdBetween(Instant from, Instant toExcl) {
        return (root, q, cb) -> {
            Predicate p = cb.conjunction();
            if (from != null)   p = cb.and(p, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (toExcl != null) p = cb.and(p, cb.lessThan(root.get("createdAt"), toExcl)); // [from, to)
            return p;
        };
    }
}
