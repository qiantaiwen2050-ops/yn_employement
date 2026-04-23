package com.yn.employment.modules.business.nationapi;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("nation_report_log")
public class NationReportLog {
    private Long id;
    private Long periodId;
    private String periodName;
    private String reportType;          // MANUAL | AUTO
    private String status;              // SENDING | SUCCESS | FAILED
    private Integer dataCount;
    private String requestSummary;
    private String responseSummary;
    private String receiptNo;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private Long parentLogId;
    private Long operatorId;
    private String operatorName;
    private String startedAt;
    private String finishedAt;
}
