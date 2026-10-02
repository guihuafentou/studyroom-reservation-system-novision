package com.campus.studyroom.controller;

import com.campus.studyroom.service.SeatSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 座位状态 SSE 事件流接口（免登录：EventSource 无法携带请求头；
 * 推送内容仅含座位号与状态文本，无个人信息）
 */
@RestController
@RequestMapping("/api/seat-events")
@RequiredArgsConstructor
public class SeatEventController {

    private final SeatSseService seatSseService;

    @GetMapping("/stream")
    public SseEmitter stream() {
        return seatSseService.subscribe();
    }
}
