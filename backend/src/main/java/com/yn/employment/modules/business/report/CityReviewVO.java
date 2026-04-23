package com.yn.employment.modules.business.report;

import lombok.Data;

@Data
public class CityReviewVO {
    private Long id;
    private Long enterpriseId;
    private Long periodId;
    private String enterpriseName;
    private String orgCode;
    private String regionCode;
    private String regionName;
    private String periodName;
    private String periodType;
    private Integer baseCount;
    private Integer currentCount;
    private String decreaseType;
    private String primaryReason;
    private String status;
    private String submittedAt;
    private String updatedAt;
    private String cityReviewer;
    private String cityReviewAt;
    private String cityReturnReason;
}
