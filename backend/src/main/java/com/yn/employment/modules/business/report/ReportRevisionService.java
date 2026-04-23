package com.yn.employment.modules.business.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yn.employment.common.BusinessException;
import com.yn.employment.common.UserContext;
import com.yn.employment.modules.system.log.SysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportRevisionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ReportRevisionMapper mapper;
    private final ReportService reportService;
    @Autowired private SysLogService sysLog;

    public ReportRevisionService(ReportRevisionMapper mapper, ReportService reportService) {
        this.mapper = mapper;
        this.reportService = reportService;
    }

    public List<ReportRevision> listForReport(Long reportId) {
        return mapper.selectList(Wrappers.<ReportRevision>lambdaQuery()
                .eq(ReportRevision::getReportId, reportId)
                .orderByAsc(ReportRevision::getRevisionSeq));
    }

    public ReportRevision latestForReport(Long reportId) {
        return mapper.selectOne(Wrappers.<ReportRevision>lambdaQuery()
                .eq(ReportRevision::getReportId, reportId)
                .orderByDesc(ReportRevision::getRevisionSeq)
                .last("LIMIT 1"));
    }

    public ReportRevision revise(Long reportId, ReviseRequest req) {
        if (req.getReason() == null || req.getReason().isBlank())
            throw new BusinessException("修改原因不能为空");
        Report raw = reportService.getRawReport(reportId);
        ReportRevision latest = latestForReport(reportId);
        Report effective = reportService.effectiveReport(raw, latest);

        // Snapshot of effective values BEFORE this revision (HashMap allows nulls)
        Map<String, Object> before = new HashMap<>();
        before.put("baseCount", effective.getBaseCount());
        before.put("currentCount", effective.getCurrentCount());
        before.put("decreaseType", effective.getDecreaseType());
        before.put("primaryReason", effective.getPrimaryReason());
        before.put("primaryReasonText", effective.getPrimaryReasonText());
        before.put("otherReason", effective.getOtherReason());

        ReportRevision r = new ReportRevision();
        r.setReportId(reportId);
        Long count = mapper.selectCount(Wrappers.<ReportRevision>lambdaQuery().eq(ReportRevision::getReportId, reportId));
        r.setRevisionSeq((count == null ? 0 : count.intValue()) + 1);
        r.setBaseCount(req.getBaseCount());
        r.setCurrentCount(req.getCurrentCount());
        r.setDecreaseType(req.getDecreaseType());
        r.setPrimaryReason(req.getPrimaryReason());
        r.setPrimaryReasonText(req.getPrimaryReasonText());
        r.setSecondaryReason(req.getSecondaryReason());
        r.setSecondaryReasonText(req.getSecondaryReasonText());
        r.setThirdReason(req.getThirdReason());
        r.setThirdReasonText(req.getThirdReasonText());
        r.setOtherReason(req.getOtherReason());
        try {
            r.setBeforeJson(JSON.writeValueAsString(before));
        } catch (JsonProcessingException e) {
            throw new BusinessException("无法序列化原始值: " + e.getMessage());
        }
        r.setReason(req.getReason().trim());
        UserContext.CurrentUser u = UserContext.require();
        r.setRevisedById(u.getId());
        r.setRevisedByName(u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername());
        r.setCreatedAt(LocalDateTime.now().format(FMT));
        mapper.insert(r);
        sysLog.log("REVISE_REPORT", "report:" + reportId,
                "省级修改 v" + r.getRevisionSeq() + " base=" + r.getBaseCount() + " curr=" + r.getCurrentCount() + "，原因：" + r.getReason());
        return r;
    }

    public Report getEffective(Long reportId) {
        Report raw = reportService.getRawReport(reportId);
        return reportService.effectiveReport(raw, latestForReport(reportId));
    }

    public static class ReviseRequest {
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
        private String reason;
        public Integer getBaseCount() { return baseCount; }
        public void setBaseCount(Integer v) { baseCount = v; }
        public Integer getCurrentCount() { return currentCount; }
        public void setCurrentCount(Integer v) { currentCount = v; }
        public String getDecreaseType() { return decreaseType; }
        public void setDecreaseType(String v) { decreaseType = v; }
        public String getPrimaryReason() { return primaryReason; }
        public void setPrimaryReason(String v) { primaryReason = v; }
        public String getPrimaryReasonText() { return primaryReasonText; }
        public void setPrimaryReasonText(String v) { primaryReasonText = v; }
        public String getSecondaryReason() { return secondaryReason; }
        public void setSecondaryReason(String v) { secondaryReason = v; }
        public String getSecondaryReasonText() { return secondaryReasonText; }
        public void setSecondaryReasonText(String v) { secondaryReasonText = v; }
        public String getThirdReason() { return thirdReason; }
        public void setThirdReason(String v) { thirdReason = v; }
        public String getThirdReasonText() { return thirdReasonText; }
        public void setThirdReasonText(String v) { thirdReasonText = v; }
        public String getOtherReason() { return otherReason; }
        public void setOtherReason(String v) { otherReason = v; }
        public String getReason() { return reason; }
        public void setReason(String v) { reason = v; }
    }
}
