package com.exam.examserver.enums;

public enum ExamTaskStatus {
    ASSIGNED,      // mới giao
    IN_PROGRESS,   // GV bắt đầu làm
    SUBMITTED,     // GV đã nộp bài (đang chờ duyệt)
    DONE,          // HEAD duyệt hoàn thành
    CANCELLED,     // HEAD huỷ (kết thúc hẳn, không nộp lại)
    REPORTED,      // GV báo lỗi/nhiệm vụ sai
    RETURNED       // HEAD từ chối & yêu cầu GV nộp lại
}



