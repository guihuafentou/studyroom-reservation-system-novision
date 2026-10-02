package com.campus.studyroom;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * 校园自习室预约管理系统 - 启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.campus.studyroom.mapper")
public class StudyRoomApplication {

    public static void main(String[] args) {
        // 评审 #30：统一时区为 Asia/Shanghai，避免定时任务与时间窗口依赖 JVM 默认时区
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(StudyRoomApplication.class, args);
        System.out.println("===== 自习室预约管理系统后端启动成功: http://localhost:8080 =====");
    }
}
