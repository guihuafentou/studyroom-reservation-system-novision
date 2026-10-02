package com.campus.studyroom.controller;

import com.campus.studyroom.common.Result;
import com.campus.studyroom.dto.WaitingDTO;
import com.campus.studyroom.entity.WaitingQueue;
import com.campus.studyroom.service.WaitingService;
import com.campus.studyroom.vo.WaitingQueueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生端候补队列接口
 */
@RestController
@RequestMapping("/api/waiting")
@RequiredArgsConstructor
public class WaitingController {

    private final WaitingService waitingService;

    @PostMapping
    public Result<WaitingQueue> enqueue(@RequestBody WaitingDTO dto) {
        return Result.ok(waitingService.enqueue(dto));
    }

    @GetMapping("/mine")
    public Result<List<WaitingQueueVO>> mine() {
        return Result.ok(waitingService.myList());
    }

    @DeleteMapping("/{id}")
    public Result<Void> quit(@PathVariable Long id) {
        waitingService.quit(id);
        return Result.ok();
    }
}
