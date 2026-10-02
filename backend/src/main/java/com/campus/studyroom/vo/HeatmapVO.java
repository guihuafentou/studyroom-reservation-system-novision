package com.campus.studyroom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时段热度点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapVO {

    private String timeRange;   // 如 08:00-08:30
    private long count;
}
