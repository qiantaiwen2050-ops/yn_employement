package com.yn.employment.modules.business.report;

import lombok.Data;

@Data
public class ReportListVO {
    private Long id;
    private Long periodId;
    private String periodName;
    private String periodType;
    private Integer baseCount;
    private Integer currentCount;
    private String status;
    private String submittedAt;
    private String updatedAt;
    private String cityReturnReason;
    private String provReturnReason;
}
