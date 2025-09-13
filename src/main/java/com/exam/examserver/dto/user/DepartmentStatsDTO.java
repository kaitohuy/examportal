package com.exam.examserver.dto.user;

public class DepartmentStatsDTO {
    private long teacherCount;
    private long subjectCount;
    private long unassignedSubjectCount; // optional

    public long getTeacherCount() {
        return teacherCount;
    }

    public void setTeacherCount(long teacherCount) {
        this.teacherCount = teacherCount;
    }

    public long getSubjectCount() {
        return subjectCount;
    }

    public void setSubjectCount(long subjectCount) {
        this.subjectCount = subjectCount;
    }

    public long getUnassignedSubjectCount() {
        return unassignedSubjectCount;
    }

    public void setUnassignedSubjectCount(long unassignedSubjectCount) {
        this.unassignedSubjectCount = unassignedSubjectCount;
    }

}

