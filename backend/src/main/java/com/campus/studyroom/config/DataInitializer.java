package com.campus.studyroom.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动初始化：若系统中不存在管理员，自动创建（用户名默认 admin，密码由环境变量 STUDYROOM_ADMIN_PASSWORD 注入，无默认值）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${studyroom.init-admin.username:admin}")
    private String adminUsername;

    @Value("${studyroom.init-admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, adminUsername));
        if (count == null || count == 0) {
            User admin = new User();
            admin.setStudentNo("000000");
            admin.setUsername(adminUsername);
            admin.setPassword(encoder.encode(adminPassword));
            admin.setRealName("系统管理员");
            admin.setRole(User.ROLE_ADMIN);
            admin.setStatus(1);
            admin.setViolationCount(0);
            admin.setCreateTime(LocalDateTime.now());
            userMapper.insert(admin);
            // 不打印明文密码：提示账号名即可，初始密码见配置 studyroom.init-admin.password
            log.info("已自动创建管理员账号: {}", adminUsername);
        }
    }
}
