package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.studyroom.entity.Announcement;
import com.campus.studyroom.mapper.AnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告服务：学生端取已发布公告（置顶优先、时间倒序）
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    /**
     * 学生端：查询已发布的公告，置顶优先、按时间倒序
     *
     * @param limit 最多返回条数
     */
    public List<Announcement> listPublished(int limit) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, Announcement.STATUS_ON)
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getCreateTime);
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return announcementMapper.selectList(wrapper);
    }
}
