package com.yn.employment.modules.business.report;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("report_attempt")
public class ReportAttempt {
    private Long id;
    private Long reportId;
    private Long enterpriseId;
    private Long periodId;
    private Integer attemptSeq;
    private Integer baseCount;
    private Integer currentCount;
    private String decreaseType;
    private String primaryReason;
    private String primaryReasonText;
    private String secondaryReason;
    private String secondaryReasonText;
    private String thirdReason;
    private String thirdReasonText;
    private String otherReason;
    private String submittedAt;
    private String status;
    private String cityReviewAt;
    private String cityReviewer;
    private String cityReturnReason;
    private String provReviewAt;
    private String provReviewer;
    private String provReturnReason;
    private String closedAt;
}
