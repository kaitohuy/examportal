package com.exam.examserver.dto.importing;

import com.exam.examserver.enums.Difficulty;

import java.util.HashMap;
import java.util.Map;

public class AdminOverviewDto {

    public Users users = new Users();
    public Departments departments = new Departments();
    public Subjects subjects = new Subjects();
    public Questions questions = new Questions();
    public Map<String, Long> coverage = new HashMap<>(); // chapter0..chapter7
    public Archive archive = new Archive();

    public static class Users {
        public long total;
        public Map<String, Long> byRole = new HashMap<>(); // ADMIN/HEAD/TEACHER
    }

    public static class Departments {
        public long count;
        public long withoutHead;
    }

    public static class Subjects {
        public long count;
        public long withoutTeachers;
    }

    public static class Questions {
        public long total;
        public Map<String, Long> byType = new HashMap<>();  // MULTIPLE_CHOICE/ESSAY
        public Map<String, Long> byLabel = new HashMap<>(); // PRACTICE/EXAM
        public Map<Difficulty, Long> byDifficulty = new HashMap<>(); // PRACTICE/EXAM
    }

    public static class Archive {
        public long pending;
        public long approved;
        public long rejected;
        public double avgReviewHours;
    }
}

