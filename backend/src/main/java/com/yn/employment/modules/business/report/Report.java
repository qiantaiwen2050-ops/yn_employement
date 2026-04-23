package com.yn.employment.modules.business.report;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("report")
public class Report {
    private Long id;
    private Long enterpriseId;
    private Long periodId;
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
    private String status;
    private String cityReviewAt;
    private String cityReviewer;
    // Allow explicit null updates: the field is cleared on approve / on enterprise re-submit.
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String cityReturnReason;
    private String provReviewAt;
    private String provReviewer;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String provReturnReason;
    private String submittedAt;
    private String createdAt;
    private String updatedAt;
    @TableLogic
    private Integer deleted;

    public static final String STATUS_DRAFT       = "DRAFT";
    public static final String STATUS_CITY_PEND   = "01";  // 待市级审核
    public static final String STATUS_CITY_OK     = "02";  // 市级已通过
    public static final String STATUS_CITY_RETURN = "03";  // 市级退回
    public static final String STATUS_PROV_PEND   = "04";  // 待省级审核
    public static final String STATUS_PROV_OK     = "05";  // 省级已通过
    public static final String STATUS_PROV_RETURN = "06";  // 省级退回
    public static final String STATUS_SUBMITTED   = "07";  // 已汇总上报
}
