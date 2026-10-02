package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.studyroom.common.BusinessException;
import com.campus.studyroom.dto.LoginDTO;
import com.campus.studyroom.dto.RegisterDTO;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.mapper.UserMapper;
import com.campus.studyroom.security.JwtUtil;
import com.campus.studyroom.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务：注册 / 登录（含防爆破锁定）/ 当前用户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 登录尝试记录：连续失败计数 + 锁定截止 + 最近失败时间（用于 TTL 清理） */
    private static final class LoginAttempt {
        int failCount;
        LocalDateTime lockUntil;
        LocalDateTime lastFailTime;

        LoginAttempt(int failCount, LocalDateTime lockUntil, LocalDateTime lastFailTime) {
            this.failCount = failCount;
            this.lockUntil = lockUntil;
            this.lastFailTime = lastFailTime;
        }
    }

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    @Value("${studyroom.rule.login-max-fail:5}")
    private int loginMaxFail;

    @Value("${studyroom.rule.login-lock-minutes:15}")
    private int loginLockMinutes;

    public User register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, dto.getStudentNo()));
        if (count != null && count > 0) {
            throw BusinessException.conflict("该学号已注册");
        }
        count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw BusinessException.conflict("用户名已存在");
        }

        User user = new User();
        user.setStudentNo(dto.getStudentNo());
        user.setUsername(dto.getUsername());
        user.setPassword(encoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setRole(User.ROLE_STUDENT);
        user.setStatus(1);
        user.setViolationCount(0);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public Map<String, Object> login(LoginDTO dto) {
        // 防爆破：连续失败达上限后锁定一段时间（内存态，重启清零）
        String key = dto.getUsername();
        LocalDateTime now = LocalDateTime.now();
        evictStale(now);

        // 原子读取锁定状态：锁定中直接拒绝；过期锁定清除
        LoginAttempt attempt = loginAttempts.compute(key, (k, v) -> {
            if (v != null && v.lockUntil != null) {
                return v.lockUntil.isAfter(now) ? v : null;
            }
            return v;
        });
        if (attempt != null && attempt.lockUntil != null) {
            long remainMinutes = java.time.Duration.between(now, attempt.lockUntil).toMinutes() + 1;
            throw BusinessException.forbidden("登录失败次数过多，账号已锁定，请 " + remainMinutes + " 分钟后再试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (user == null || !encoder.matches(dto.getPassword(), user.getPassword())) {
            // 原子计数：并发失败请求不会互相覆盖
            LoginAttempt finalState = loginAttempts.compute(key, (k, v) -> {
                int fail = (v == null ? 0 : v.failCount) + 1;
                if (fail >= loginMaxFail) {
                    return new LoginAttempt(0, now.plusMinutes(loginLockMinutes), now);
                }
                return new LoginAttempt(fail, null, now);
            });
            if (finalState.lockUntil != null) {
                log.warn("用户 {} 连续登录失败 {} 次，锁定 {} 分钟", key, loginMaxFail, loginLockMinutes);
                throw BusinessException.forbidden("登录失败次数过多，账号已锁定，请 " + loginLockMinutes + " 分钟后重试");
            }
            throw BusinessException.badRequest("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw BusinessException.forbidden("账号已被禁用，请联系管理员");
        }
        // 登录成功：清零失败计数
        loginAttempts.remove(key);

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return data;
    }

    /**
     * TTL 清理：移除超过一个锁定周期的失败计数条目，防止 Map 无界增长（DoS 防护）
     */
    private void evictStale(LocalDateTime now) {
        if (loginAttempts.size() > 20000) {
            loginAttempts.clear();
            return;
        }
        loginAttempts.entrySet().removeIf(e -> {
            LoginAttempt v = e.getValue();
            if (v.lockUntil != null) {
                return false;
            }
            return v.failCount > 0 && v.lastFailTime != null
                    && v.lastFailTime.isBefore(now.minusMinutes(loginLockMinutes));
        });
    }

    public User me() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        return user;
    }
}
