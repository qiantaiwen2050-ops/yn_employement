package com.yn.employment.modules.system.period;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_period")
public class Period {
    private Long id;
    private String name;
    private Integer year;
    private String periodType;     // HALF_MONTH | MONTH
    private Integer seqInYear;     // 1..15
    private String startDate;      // YYYY-MM-DD
    private String endDate;
    private String status;         // OPEN | CLOSED
    private String createdAt;
}
