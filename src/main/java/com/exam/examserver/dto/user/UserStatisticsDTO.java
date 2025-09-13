package com.exam.examserver.dto.user;

public class UserStatisticsDTO {
    private long totalUsers;
    private long totalDepartments;
    private long totalTeachers;
    private long totalHeads;
    // getters/setters

    public UserStatisticsDTO() {
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalTeachers() {
        return totalTeachers;
    }

    public void setTotalTeachers(long totalTeachers) {
        this.totalTeachers = totalTeachers;
    }

    public long getTotalDepartments() {
        return totalDepartments;
    }

    public void setTotalDepartments(long totalDepartments) {
        this.totalDepartments = totalDepartments;
    }

    public long getTotalHeads() {
        return totalHeads;
    }

    public void setTotalHeads(long totalHeads) {
        this.totalHeads = totalHeads;
    }
}
