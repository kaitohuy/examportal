package com.exam.examserver.enums;

public enum ExamTaskStatus {
    ASSIGNED,      // mới giao
    IN_PROGRESS,   // giáo viên bắt đầu làm (optional)
    DONE,          // giáo viên đánh dấu hoàn thành
    CANCELLED      // HEAD huỷ
}

