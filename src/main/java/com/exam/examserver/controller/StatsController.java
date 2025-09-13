package com.exam.examserver.controller;

import com.exam.examserver.dto.importing.AdminOverviewDto;
import com.exam.examserver.dto.importing.HeadOverviewDto;
import com.exam.examserver.dto.importing.TeacherOverviewDto;
import com.exam.examserver.service.impl.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@CrossOrigin("*")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    // GET /api/stats/admin/overview?from=2025-01-01&to=2025-01-31
    @GetMapping("/admin/overview")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminOverviewDto adminOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.getAdminOverview(from, to);
    }

    @GetMapping("/head/overview")
    @PreAuthorize("hasAnyAuthority('HEAD','ADMIN')")
    public HeadOverviewDto headOverview(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.getHeadOverview(deptId, from, to);
    }

    @GetMapping("/teacher/overview")
    @PreAuthorize("hasAnyAuthority('TEACHER','ADMIN','HEAD')")
    public TeacherOverviewDto teacherOverview(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.getTeacherOverview(userId, from, to);
    }
}
