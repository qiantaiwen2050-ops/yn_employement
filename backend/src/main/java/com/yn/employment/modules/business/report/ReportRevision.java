package com.yn.employment.modules.business.report;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("report_revision")
public class ReportRevision {
    private Long id;
    private Long reportId;
    private Integer revisionSeq;
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
    private String beforeJson;
    private Long revisedById;
    private String revisedByName;
    private String reason;
    private String createdAt;
}
