package com.campus.studyroom.controller;

import com.campus.studyroom.common.Result;
import com.campus.studyroom.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 学生端学习时长统计（评审 #27）
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StudyStatsController {

    private final ReservationService reservationService;

    @GetMapping("/study")
    public Result<Map<String, Object>> study() {
        return Result.ok(reservationService.studyStats());
    }
}
