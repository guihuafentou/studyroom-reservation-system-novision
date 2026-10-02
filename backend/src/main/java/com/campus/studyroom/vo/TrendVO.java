package com.campus.studyroom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 趋势点：近 N 日预约量
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendVO {

    private String date;
    private long count;
}
