package com.campus.studyroom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 座位状态 SSE 推送服务：在线前端订阅后，座位状态变化实时推送
 * - 订阅设 30 分钟超时，定时心跳探测+清理，防止异常断连导致连接/内存泄漏
 * - 广播 JSON 由 Jackson 序列化，避免手工拼接转义问题
 */
@Slf4j
@Component
public class SeatSseService {

    /** 单连接最长存活时间（毫秒） */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 订阅事件流
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        String key = emitter.toString();
        emitters.put(key, emitter);
        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> emitters.remove(key));
        emitter.onError(e -> emitters.remove(key));
        // 发送连接确认事件
        try {
            emitter.send(SseEmitter.event().name("init").data("{\"type\":\"init\"}"));
        } catch (IOException e) {
            emitters.remove(key);
        }
        return emitter;
    }

    /**
     * 向所有在线订阅者推送座位状态事件
     *
     * @param seatNo 座位号
     * @param status 状态文本（空闲/预约/使用中/告警等）
     * @param extra  附加字段（如告警类型）
     */
    public void broadcastSeatStatus(String seatNo, String status, String extra) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("seatNo", seatNo);
        payload.put("status", status);
        payload.put("extra", extra);
        String data = toJson(payload);
        for (Map.Entry<String, SseEmitter> e : emitters.entrySet()) {
            try {
                e.getValue().send(SseEmitter.event().name("seat").data(data));
            } catch (IOException ex) {
                emitters.remove(e.getKey());
            }
        }
    }

    /**
     * 广播违规占座告警事件（管理端实时收到）
     */
    public void broadcastAlert(String seatNo, String message) {
        broadcastSeatStatus(seatNo, "ALERT", message);
    }

    /**
     * 定时心跳：向所有连接发送注释帧，探测已断开连接并清理
     */
    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, SseEmitter> e : emitters.entrySet()) {
            try {
                e.getValue().send(SseEmitter.event().comment("hb"));
            } catch (IOException ex) {
                emitters.remove(e.getKey());
            }
        }
    }

    public int onlineCount() {
        return emitters.size();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("SSE JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
