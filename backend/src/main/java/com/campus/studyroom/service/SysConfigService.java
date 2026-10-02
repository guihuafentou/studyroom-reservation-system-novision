package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.studyroom.entity.SysConfig;
import com.campus.studyroom.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数服务：启动时全量载入内存缓存，读取零 DB 开销；
 * 管理后台修改后调用 reload() 刷新缓存，规则即时生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    /** 参数键 → 参数值 内存缓存 */
    private volatile Map<String, String> cache = new HashMap<>();

    @PostConstruct
    public void init() {
        reload();
        log.info("系统参数加载完成，共 {} 项", cache.size());
    }

    /** 从数据库全量刷新缓存（管理后台保存参数后调用） */
    public synchronized void reload() {
        List<SysConfig> list = sysConfigMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, String> map = new HashMap<>();
        for (SysConfig c : list) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        this.cache = map;
    }

    public String get(String key) {
        return cache.get(key);
    }

    public String get(String key, String defaultValue) {
        String v = cache.get(key);
        return v == null || v.isBlank() ? defaultValue : v.trim();
    }

    public int getInt(String key, int defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("系统参数 {} 值非法: {}, 使用默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return "1".equals(v.trim()) || "true".equalsIgnoreCase(v.trim());
    }

    /** 保存单个参数并刷新缓存 */
    public void save(String key, String value) {
        SysConfig cfg = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            cfg.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.insert(cfg);
        } else {
            cfg.setConfigValue(value);
            cfg.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.updateById(cfg);
        }
        reload();
    }
}
