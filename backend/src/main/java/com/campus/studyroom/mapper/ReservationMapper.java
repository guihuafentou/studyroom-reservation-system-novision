package com.campus.studyroom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.studyroom.entity.Reservation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ReservationMapper extends BaseMapper<Reservation> {

    /**
     * 按日期统计预约数（不含已取消）
     */
    @Select("SELECT reserve_date AS date, COUNT(*) AS count FROM reservation " +
            "WHERE reserve_date BETWEEN #{start} AND #{end} AND status != 3 " +
            "GROUP BY reserve_date ORDER BY reserve_date")
    List<Map<String, Object>> countByDate(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * 按时段统计预约数（不含已取消），跨房间合并展示
     */
    @Select("SELECT CONCAT(s.start_time, '-', s.end_time) AS time_range, COUNT(*) AS count " +
            "FROM reservation r JOIN slot s ON r.slot_id = s.id " +
            "WHERE r.reserve_date BETWEEN #{start} AND #{end} AND r.status != 3 " +
            "GROUP BY s.id, s.start_time, s.end_time ORDER BY s.start_time")
    List<Map<String, Object>> countBySlot(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * 弹性室区间重叠检测：目标区间 [start,end) 与该座位任何进行中预约（待签到/已签到）冲突的记录
     * 覆盖两种记录：弹性记录(start_time/end_time) 与 离散记录(slot_id+reserve_date)
     * 使用 FOR SHARE 锁定读：避免 REPEATABLE READ 快照导致并发下看不到已提交插入（行锁形同虚设）；
     * 离散分支用 TIMESTAMP(reserve_date, slot 时间) 参与比较，跨午夜弹性区间也能命中次日离散记录。
     */
    @Select("SELECT r.id FROM reservation r LEFT JOIN slot s ON r.slot_id = s.id " +
            "WHERE r.seat_id = #{seatId} AND r.status IN (0, 1) AND (" +
            "  (r.start_time IS NOT NULL AND r.start_time < #{end} AND r.end_time > #{start}) " +
            "  OR " +
            "  (r.slot_id IS NOT NULL " +
            "   AND TIMESTAMP(r.reserve_date, s.start_time) < #{end} " +
            "   AND TIMESTAMP(r.reserve_date, s.end_time) > #{start})" +
            ") FOR SHARE")
    List<Long> findOverlap(@Param("seatId") Long seatId,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);
}
