package com.campus.studyroom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.studyroom.entity.Seat;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 行锁读取（P4 并发锁）：弹性室创建预约时锁定座位行，
     * 保证区间重叠校验 + 落库的原子性
     */
    @Select("SELECT * FROM seat WHERE id = #{id} FOR UPDATE")
    Seat selectByIdForUpdate(@Param("id") Long id);
}
