package com.campus.studyroom.controller;

import com.campus.studyroom.common.Result;
import com.campus.studyroom.dto.ReserveDTO;
import com.campus.studyroom.entity.Reservation;
import com.campus.studyroom.service.ReservationService;
import com.campus.studyroom.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预约接口
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public Result<List<Reservation>> create(@Valid @RequestBody ReserveDTO dto) {
        return Result.ok(reservationService.create(dto));
    }

    @GetMapping("/mine")
    public Result<List<ReservationVO>> mine() {
        return Result.ok(reservationService.myReservations());
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return Result.ok();
    }

    @PutMapping("/{id}/sign")
    public Result<ReservationVO> sign(@PathVariable Long id) {
        return Result.ok(reservationService.sign(id));
    }
}
