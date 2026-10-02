package com.campus.studyroom.controller;

import com.campus.studyroom.common.Result;
import com.campus.studyroom.entity.Announcement;
import com.campus.studyroom.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生端公告接口（需登录，JWT 拦截器统一校验）
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public Result<List<Announcement>> list(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(announcementService.listPublished(Math.max(1, Math.min(limit, 50))));
    }
}
