package com.exam.examserver.dto.importing;

import com.exam.examserver.enums.Difficulty;

import java.time.Instant;
import java.util.*;

public class TeacherOverviewDto {

    public Subjects subjects = new Subjects();
    public Questions questions = new Questions();
    public Map<String, Long> coverage = new HashMap<>(); // chapter0..chapter7
    public Archive archive = new Archive();

    public static class Subjects {
        public long assigned; // số môn được phân công
        public List<SubjectContribution> myContribTop = new ArrayList<>(); // top 5 đóng góp theo môn
    }

    public static class SubjectContribution {
        public Long subjectId;
        public String subjectName;
        public long myQuestions;      // số câu tôi tạo cho môn
        public long subjectTotal;     // tổng câu của môn
        public SubjectContribution(Long subjectId, String subjectName, long myQuestions, long subjectTotal) {
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.myQuestions = myQuestions;
            this.subjectTotal = subjectTotal;
        }
    }

    public static class Questions {
        public long total;
        public Map<String, Long> byType = new HashMap<>();    // MULTIPLE_CHOICE / ESSAY
        public Map<String, Long> byLabel = new HashMap<>();   // PRACTICE / EXAM
        public Map<Difficulty, Long> byDifficulty = new EnumMap<>(Difficulty.class); // tuỳ chọn
    }

    public static class Archive {
        public long pending;
        public long approved;
        public long rejected;
        public double avgReviewHours;           // chỉ file của tôi
        public List<PendingItem> pendingSoon = new ArrayList<>(); // 5 item gần hạn
    }

    public static class PendingItem {
        public Long id;
        public String filename;
        public String variant;       // EXAM / PRACTICE (nếu EXPORT)
        public String reviewStatus;  // PENDING
        public Instant createdAt;
        public Instant reviewDeadline;
        public String subjectName;
        public PendingItem(Long id, String filename, String variant, String reviewStatus,
                           Instant createdAt, Instant reviewDeadline, String subjectName) {
            this.id = id; this.filename = filename; this.variant = variant; this.reviewStatus = reviewStatus;
            this.createdAt = createdAt; this.reviewDeadline = reviewDeadline; this.subjectName = subjectName;
        }
    }
}
