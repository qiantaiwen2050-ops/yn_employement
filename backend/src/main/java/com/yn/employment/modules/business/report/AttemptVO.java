package com.yn.employment.modules.business.report;

import lombok.Data;

@Data
public class AttemptVO {
    private Long id;
    private Long reportId;
    private Long periodId;
    private String periodName;
    private String periodType;
    private Integer attemptSeq;
    private Integer baseCount;
    private Integer currentCount;
    private String status;
    private String submittedAt;
    private String cityReviewAt;
    private String cityReviewer;
    private String cityReturnReason;
    private String provReviewAt;
    private String provReviewer;
    private String provReturnReason;
    private String closedAt;
}
