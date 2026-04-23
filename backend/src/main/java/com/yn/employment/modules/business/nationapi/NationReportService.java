package com.yn.employment.modules.business.nationapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import com.yn.employment.common.UserContext;
import com.yn.employment.modules.business.report.Report;
import com.yn.employment.modules.business.report.ReportMapper;
import com.yn.employment.modules.system.log.SysLogService;
import com.yn.employment.modules.system.period.Period;
import com.yn.employment.modules.system.period.PeriodService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class NationReportService {

    public static final String TYPE_MANUAL = "MANUAL";
    public static final String TYPE_AUTO   = "AUTO";
    public static final String STATUS_SENDING = "SENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED  = "FAILED";

    public static final int MAX_RETRY = 5;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NationReportLogMapper logMapper;
    private final NationMockController nationMock;
    private final ReportMapper reportMapper;
    private final PeriodService periodService;
    @Autowired private SysLogService sysLog;

    public NationReportService(NationReportLogMapper logMapper, NationMockController nationMock,
                               ReportMapper reportMapper, PeriodService periodService) {
        this.logMapper = logMapper;
        this.nationMock = nationMock;
        this.reportMapper = reportMapper;
        this.periodService = periodService;
    }

    /** Manual upload — bundles all province-approved (status 05) reports for the given period. */
    @Transactional
    public NationReportLog uploadPeriod(Long periodId) {
        Period p = periodService.getById(periodId);
        if (p == null) throw new BusinessException("调查期不存在");
        List<Report> reports = reportMapper.selectList(Wrappers.<Report>lambdaQuery()
                .eq(Report::getPeriodId, periodId)
                .eq(Report::getStatus, Report.STATUS_PROV_OK));
        if (reports.isEmpty())
            throw new BusinessException("调查期 [" + p.getName() + "] 暂无「省级已通过」状态的报表可上报");
        return runUpload(p, reports, TYPE_MANUAL, null);
    }

    /** Internal — submit a specific list of report ids; used by M4 batch-submit-nation. */
    @Transactional
    public Map<String, Object> uploadByReportIds(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) throw new BusinessException("请选择至少一条记录");
        List<Report> reports = reportMapper.selectBatchIds(reportIds);
        if (reports.size() != reportIds.size()) throw new BusinessException("部分数据不存在");
        // group by period
        Map<Long, List<Report>> byPeriod = new java.util.LinkedHashMap<>();
        for (Report r : reports) byPeriod.computeIfAbsent(r.getPeriodId(), k -> new java.util.ArrayList<>()).add(r);

        int totalSuccess = 0;
        List<Map<String, Object>> failures = new java.util.ArrayList<>();
        for (var en : byPeriod.entrySet()) {
            Period p = periodService.getById(en.getKey());
            List<Report> grp = en.getValue();
            // skip non-PROV_OK ones
            List<Report> eligible = grp.stream().filter(r -> Report.STATUS_PROV_OK.equals(r.getStatus())).toList();
            int skipped = grp.size() - eligible.size();
            if (eligible.isEmpty()) {
                for (Report r : grp) failures.add(Map.of("id", r.getId(), "reason", "非「省级已通过」状态，跳过"));
                continue;
            }
            NationReportLog log = runUpload(p, eligible, TYPE_MANUAL, null);
            if (STATUS_SUCCESS.equals(log.getStatus())) {
                totalSuccess += eligible.size();
            } else {
                for (Report r : eligible) failures.add(Map.of("id", r.getId(), "reason", "国家系统拒收: " + log.getErrorMessage()));
            }
            for (int i = 0; i < skipped; i++) {
                // already added above
            }
        }
        return Map.of(
                "total", reports.size(),
                "success", totalSuccess,
                "failed", failures.size(),
                "failures", failures);
    }

    @Transactional
    public NationReportLog retry(Long logId) {
        NationReportLog prev = logMapper.selectById(logId);
        if (prev == null) throw new BusinessException("上报记录不存在");
        if (!STATUS_FAILED.equals(prev.getStatus()))
            throw new BusinessException("仅失败的记录可重试");
        if (prev.getRetryCount() != null && prev.getRetryCount() >= MAX_RETRY)
            throw new BusinessException("已达最大重试次数 (" + MAX_RETRY + ")，请人工核实问题");
        Period p = periodService.getById(prev.getPeriodId());
        if (p == null) throw new BusinessException("调查期已不存在");
        List<Report> reports = reportMapper.selectList(Wrappers.<Report>lambdaQuery()
                .eq(Report::getPeriodId, prev.getPeriodId())
                .eq(Report::getStatus, Report.STATUS_PROV_OK));
        if (reports.isEmpty())
            throw new BusinessException("没有需要上报的「省级已通过」数据（可能已被其他流程提前上报）");
        Long parentId = prev.getParentLogId() != null ? prev.getParentLogId() : prev.getId();
        int nextRetry = (prev.getRetryCount() == null ? 0 : prev.getRetryCount()) + 1;
        return runUpload(p, reports, prev.getReportType(), new RetryContext(parentId, nextRetry));
    }

    public List<NationReportLog> listLogs(String status, String reportType, Long periodId) {
        LambdaQueryWrapper<NationReportLog> q = Wrappers.<NationReportLog>lambdaQuery()
                .eq(status != null && !status.isBlank(), NationReportLog::getStatus, status)
                .eq(reportType != null && !reportType.isBlank(), NationReportLog::getReportType, reportType)
                .eq(periodId != null, NationReportLog::getPeriodId, periodId)
                .orderByDesc(NationReportLog::getStartedAt);
        return logMapper.selectList(q);
    }

    public NationReportLog getLog(Long id) {
        NationReportLog log = logMapper.selectById(id);
        if (log == null) throw new BusinessException("记录不存在");
        return log;
    }

    // -------------------------------------------------------------

    private NationReportLog runUpload(Period p, List<Report> reports, String reportType, RetryContext retry) {
        UserContext.CurrentUser u = UserContext.get();   // null if scheduled
        String now = LocalDateTime.now().format(FMT);

        NationReportLog log = new NationReportLog();
        log.setPeriodId(p.getId());
        log.setPeriodName(p.getName());
        log.setReportType(reportType);
        log.setStatus(STATUS_SENDING);
        log.setDataCount(reports.size());
        long totalCurrent = reports.stream().mapToLong(r -> r.getCurrentCount() == null ? 0 : r.getCurrentCount()).sum();
        log.setRequestSummary("共 " + reports.size() + " 条；调查期合计就业人数 " + totalCurrent);
        log.setRetryCount(retry == null ? 0 : retry.retrySeq);
        log.setParentLogId(retry == null ? null : retry.parentLogId);
        log.setOperatorId(u != null ? u.getId() : null);
        log.setOperatorName(u != null
                ? (u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername())
                : "系统自动");
        log.setStartedAt(now);
        logMapper.insert(log);

        // Call mock
        NationMockController.MockRequest req = new NationMockController.MockRequest();
        req.setPeriodName(p.getName());
        req.setDataCount(reports.size());
        req.setSummary(log.getRequestSummary());
        NationMockController.MockResponse resp = nationMock.upload(req);

        log.setFinishedAt(LocalDateTime.now().format(FMT));
        if (resp.isOk()) {
            log.setStatus(STATUS_SUCCESS);
            log.setReceiptNo(resp.getReceiptNo());
            log.setResponseSummary(resp.getMessage());
            // Bump reports to status=07 (已汇总上报)
            for (Report r : reports) {
                r.setStatus(Report.STATUS_SUBMITTED);
                r.setUpdatedAt(log.getFinishedAt());
                reportMapper.updateById(r);
            }
        } else {
            log.setStatus(STATUS_FAILED);
            log.setErrorCode(resp.getErrorCode());
            log.setErrorMessage(resp.getMessage());
            log.setResponseSummary("失败: " + resp.getMessage());
        }
        logMapper.updateById(log);
        if (sysLog != null) sysLog.log("NATION_UPLOAD", "period:" + p.getId() + ",log:" + log.getId(),
                "国家上报 [" + p.getName() + "] " + log.getStatus() + (log.getStatus().equals(STATUS_SUCCESS) ? " 回执 " + log.getReceiptNo() : " 失败：" + log.getErrorMessage()));
        return log;
    }

    @Data
    @AllArgsConstructor
    private static class RetryContext {
        private Long parentLogId;
        private int retrySeq;
    }
}
