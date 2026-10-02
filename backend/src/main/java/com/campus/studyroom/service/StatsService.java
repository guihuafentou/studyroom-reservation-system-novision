package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.studyroom.entity.Reservation;
import com.campus.studyroom.entity.Seat;
import com.campus.studyroom.mapper.ReservationMapper;
import com.campus.studyroom.mapper.SeatMapper;
import com.campus.studyroom.vo.HeatmapVO;
import com.campus.studyroom.vo.StatsOverviewVO;
import com.campus.studyroom.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统计报表服务
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;

    /**
     * 今日概览
     */
    public StatsOverviewVO overview() {
        LocalDate today = LocalDate.now();
        StatsOverviewVO vo = new StatsOverviewVO();
        // 口径与 trend 一致：预约量不含已取消
        Long todayRes = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReserveDate, today)
                .ne(Reservation::getStatus, Reservation.STATUS_CANCELED));
        vo.setTodayReservations(todayRes == null ? 0 : todayRes);
        vo.setTodaySigned(countByDateAndStatus(today, Reservation.STATUS_SIGNED));
        vo.setTodayFinished(countByDateAndStatus(today, Reservation.STATUS_FINISHED));
        vo.setTodayViolations(countByDateAndStatus(today, Reservation.STATUS_VIOLATED));
        vo.setTotalSeats(seatMapper.selectCount(new LambdaQueryWrapper<Seat>()
                .ne(Seat::getStatus, Seat.STATUS_DISABLED)));
        vo.setInUseSeats(seatMapper.selectCount(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getStatus, Seat.STATUS_IN_USE)));

        if (vo.getTotalSeats() > 0) {
            vo.setOccupancyRate(BigDecimal.valueOf(vo.getInUseSeats())
                    .divide(BigDecimal.valueOf(vo.getTotalSeats()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
        } else {
            vo.setOccupancyRate(BigDecimal.ZERO);
        }
        if (vo.getTodayReservations() > 0) {
            vo.setViolationRate(BigDecimal.valueOf(vo.getTodayViolations())
                    .divide(BigDecimal.valueOf(vo.getTodayReservations()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
        } else {
            vo.setViolationRate(BigDecimal.ZERO);
        }
        return vo;
    }

    private long countByDateAndStatus(LocalDate date, Integer status) {
        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReserveDate, date);
        if (status != null) {
            wrapper.eq(Reservation::getStatus, status);
        }
        Long count = reservationMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    /**
     * 近 N 日预约趋势（不含已取消）
     */
    public List<TrendVO> trend(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        List<Map<String, Object>> rows = reservationMapper.countByDate(start, end);
        Map<String, Long> map = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("date")), ((Number) row.get("count")).longValue());
        }
        List<TrendVO> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            result.add(new TrendVO(d.toString(), map.getOrDefault(d.toString(), 0L)));
        }
        return result;
    }

    /**
     * 时段热度（近 7 日）
     */
    public List<HeatmapVO> heatmap() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6L);
        List<Map<String, Object>> rows = reservationMapper.countBySlot(start, end);
        List<HeatmapVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String range = String.valueOf(row.get("time_range"));
            result.add(new HeatmapVO(range, ((Number) row.get("count")).longValue()));
        }
        return result;
    }
}
