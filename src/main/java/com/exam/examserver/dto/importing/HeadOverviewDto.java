package com.exam.examserver.dto.importing;

import java.util.ArrayList;
import java.util.List;

public class HeadOverviewDto extends AdminOverviewDto {
    public List<TopSubject> topSubjects = new ArrayList<>();
    public List<TopTeacher> topTeachers = new ArrayList<>();

    public static class TopSubject {
        public Long subjectId;
        public String subjectName;
        public long count;
        public TopSubject(Long subjectId, String subjectName, long count) {
            this.subjectId = subjectId; this.subjectName = subjectName; this.count = count;
        }
    }
    public static class TopTeacher {
        public Long userId;
        public String teacherName;
        public long count;
        public TopTeacher(Long userId, String teacherName, long count) {
            this.userId = userId; this.teacherName = teacherName; this.count = count;
        }
    }
}
