package com.exam.examserver.repo.spec;
import org.springframework.data.jpa.domain.Specification;
import com.exam.examserver.model.exam.QuestionBundle;
import com.exam.examserver.enums.RecordStatus;

public final class QuestionBundleSpecs {
    private QuestionBundleSpecs() {}

    public static Specification<QuestionBundle> bySubjectId(Long subjectId) {
        return (root, cq, cb) -> (subjectId == null) ? cb.conjunction()
                : cb.equal(root.get("subject").get("id"), subjectId);
    }

    public static Specification<QuestionBundle> status(RecordStatus status) {
        return (root, cq, cb) -> (status == null) ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<QuestionBundle> titleContains(String txt) {
        return (root, cq, cb) -> {
            if (txt == null || txt.isBlank()) return cb.conjunction();
            return cb.like(cb.lower(root.get("title")), "%" + txt.toLowerCase() + "%");
        };
    }
}

