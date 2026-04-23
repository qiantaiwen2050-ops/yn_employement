package com.yn.employment.modules.business.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import com.yn.employment.modules.business.filing.EnterpriseInfo;
import com.yn.employment.modules.business.filing.EnterpriseInfoMapper;
import com.yn.employment.modules.business.filing.FilingService;
import com.yn.employment.modules.system.log.SysLogService;
import com.yn.employment.modules.system.period.Period;
import com.yn.employment.modules.system.period.PeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> EDITABLE_STATUSES = Set.of(
            Report.STATUS_DRAFT, Report.STATUS_CITY_RETURN, Report.STATUS_PROV_RETURN);

    private final ReportMapper mapper;
    private final ReportAttemptMapper attemptMapper;
    private final FilingService filingService;
    private final PeriodService periodService;
    private final EnterpriseInfoMapper enterpriseInfoMapper;
    @Autowired private SysLogService sysLog;

    public ReportService(ReportMapper mapper, ReportAttemptMapper attemptMapper, FilingService filingService,
                         PeriodService periodService, EnterpriseInfoMapper enterpriseInfoMapper) {
        this.mapper = mapper;
        this.attemptMapper = attemptMapper;
        this.filingService = filingService;
        this.periodService = periodService;
        this.enterpriseInfoMapper = enterpriseInfoMapper;
    }

    /** Get-or-create draft for current enterprise on a given period. */
    public Report getOrCreateDraft(Long userId, Long periodId) {
        EnterpriseInfo ei = ensureApproved(userId);
        Period period = periodService.getById(periodId);
        if (period == null) throw new BusinessException("调查期不存在");
        if (!PeriodService.STATUS_OPEN.equals(period.getStatus()))
            throw new BusinessException("当前调查期未开放，无法填报");
        Report r = mapper.selectOne(Wrappers.<Report>lambdaQuery()
                .eq(Report::getEnterpriseId, ei.getId())
                .eq(Report::getPeriodId, periodId));
        if (r == null) {
            r = new Report();
            r.setEnterpriseId(ei.getId());
            r.setPeriodId(periodId);
            r.setStatus(Report.STATUS_DRAFT);
            mapper.insert(r);
        }
        return r;
    }

    public Report saveDraft(Long userId, Report dto) {
        Report cur = loadEditable(userId, dto.getId());
        applyEditable(cur, dto);
        validateNumbers(cur);
        validateReasonOrderingAndUnique(cur);
        cur.setUpdatedAt(now());
        // Saving a return → goes back to draft for clarity
        if (Report.STATUS_CITY_RETURN.equals(cur.getStatus()) || Report.STATUS_PROV_RETURN.equals(cur.getStatus())) {
            // keep return reason visible; status remains so user knows it's a re-edit
        }
        mapper.updateById(cur);
        return cur;
    }

    public Report submit(Long userId, Report dto) {
        Report cur = loadEditable(userId, dto.getId());
        applyEditable(cur, dto);
        validateRequired(cur);
        // Re-submitting after a return: clear stale return reasons on the *current* row.
        // (The previous attempt row preserves them in history.)
        cur.setCityReturnReason(null);
        cur.setProvReturnReason(null);
        cur.setStatus(Report.STATUS_CITY_PEND);
        cur.setSubmittedAt(now());
        cur.setUpdatedAt(cur.getSubmittedAt());
        mapper.updateById(cur);
        // Snapshot this submission as a new attempt
        recordAttempt(cur);
        sysLog.log("SUBMIT_REPORT", "report:" + cur.getId(),
                "企业上报调查期数据 periodId=" + cur.getPeriodId() + " base=" + cur.getBaseCount() + " curr=" + cur.getCurrentCount());
        return cur;
    }

    /** Append a new attempt row mirroring the current Report state at submit time. */
    private void recordAttempt(Report r) {
        Long maxSeq = attemptMapper.selectCount(Wrappers.<ReportAttempt>lambdaQuery()
                .eq(ReportAttempt::getReportId, r.getId()));
        ReportAttempt a = new ReportAttempt();
        a.setReportId(r.getId());
        a.setEnterpriseId(r.getEnterpriseId());
        a.setPeriodId(r.getPeriodId());
        a.setAttemptSeq((maxSeq == null ? 0 : maxSeq.intValue()) + 1);
        a.setBaseCount(r.getBaseCount());
        a.setCurrentCount(r.getCurrentCount());
        a.setDecreaseType(r.getDecreaseType());
        a.setPrimaryReason(r.getPrimaryReason());
        a.setPrimaryReasonText(r.getPrimaryReasonText());
        a.setSecondaryReason(r.getSecondaryReason());
        a.setSecondaryReasonText(r.getSecondaryReasonText());
        a.setThirdReason(r.getThirdReason());
        a.setThirdReasonText(r.getThirdReasonText());
        a.setOtherReason(r.getOtherReason());
        a.setSubmittedAt(r.getSubmittedAt());
        a.setStatus(r.getStatus());
        attemptMapper.insert(a);
    }

    /** Find the live (still-open) attempt for a report, if any. */
    private ReportAttempt latestOpenAttempt(Long reportId) {
        return attemptMapper.selectOne(Wrappers.<ReportAttempt>lambdaQuery()
                .eq(ReportAttempt::getReportId, reportId)
                .isNull(ReportAttempt::getClosedAt)
                .orderByDesc(ReportAttempt::getAttemptSeq)
                .last("LIMIT 1"));
    }

    public List<ReportListVO> listMine(Long userId, Long periodId, String status) {
        EnterpriseInfo ei = filingService.getByUserId(userId);
        if (ei == null) return List.of();
        List<Report> reports = mapper.selectList(Wrappers.<Report>lambdaQuery()
                .eq(Report::getEnterpriseId, ei.getId())
                .eq(periodId != null, Report::getPeriodId, periodId)
                .eq(status != null && !status.isEmpty(), Report::getStatus, status)
                .orderByDesc(Report::getUpdatedAt));
        return reports.stream().map(r -> {
            Period p = periodService.getById(r.getPeriodId());
            ReportListVO vo = new ReportListVO();
            vo.setId(r.getId());
            vo.setPeriodId(r.getPeriodId());
            vo.setPeriodName(p != null ? p.getName() : "");
            vo.setPeriodType(p != null ? p.getPeriodType() : "");
            vo.setBaseCount(r.getBaseCount());
            vo.setCurrentCount(r.getCurrentCount());
            vo.setStatus(r.getStatus());
            vo.setSubmittedAt(r.getSubmittedAt());
            vo.setUpdatedAt(r.getUpdatedAt());
            vo.setCityReturnReason(r.getCityReturnReason());
            vo.setProvReturnReason(r.getProvReturnReason());
            return vo;
        }).toList();
    }

    public Report detail(Long userId, Long reportId) {
        EnterpriseInfo ei = filingService.getByUserId(userId);
        if (ei == null) throw new BusinessException("企业未备案");
        Report r = mapper.selectById(reportId);
        if (r == null) throw new BusinessException("数据不存在");
        if (!r.getEnterpriseId().equals(ei.getId())) throw new BusinessException(403, "无权查看");
        return r;
    }

    private Report loadEditable(Long userId, Long id) {
        if (id == null) throw new BusinessException("缺少报表 id");
        EnterpriseInfo ei = ensureApproved(userId);
        Report r = mapper.selectById(id);
        if (r == null) throw new BusinessException("报表不存在");
        if (!r.getEnterpriseId().equals(ei.getId())) throw new BusinessException(403, "无权操作");
        if (!EDITABLE_STATUSES.contains(r.getStatus()))
            throw new BusinessException("当前状态不可编辑（" + r.getStatus() + "）");
        Period period = periodService.getById(r.getPeriodId());
        if (period == null || !PeriodService.STATUS_OPEN.equals(period.getStatus()))
            throw new BusinessException("调查期已关闭，无法操作");
        return r;
    }

    private EnterpriseInfo ensureApproved(Long userId) {
        EnterpriseInfo ei = filingService.getByUserId(userId);
        if (ei == null || !EnterpriseInfo.STATUS_APPROVED.equals(ei.getFilingStatus())) {
            throw new BusinessException("企业备案尚未通过，无法填报数据");
        }
        return ei;
    }

    private void applyEditable(Report cur, Report dto) {
        cur.setBaseCount(dto.getBaseCount());
        cur.setCurrentCount(dto.getCurrentCount());
        cur.setDecreaseType(trim(dto.getDecreaseType()));
        cur.setPrimaryReason(trim(dto.getPrimaryReason()));
        cur.setPrimaryReasonText(trim(dto.getPrimaryReasonText()));
        cur.setSecondaryReason(trim(dto.getSecondaryReason()));
        cur.setSecondaryReasonText(trim(dto.getSecondaryReasonText()));
        cur.setThirdReason(trim(dto.getThirdReason()));
        cur.setThirdReasonText(trim(dto.getThirdReasonText()));
        cur.setOtherReason(trim(dto.getOtherReason()));
    }

    private void validateNumbers(Report r) {
        if (r.getBaseCount() != null && r.getBaseCount() < 0)
            throw new BusinessException("建档期就业人数不能为负数");
        if (r.getCurrentCount() != null && r.getCurrentCount() < 0)
            throw new BusinessException("调查期就业人数不能为负数");
    }

    /** SRS §4.5.3.4 — current < base ⇒ decrease type & primary reason required. */
    private void validateRequired(Report r) {
        if (r.getBaseCount() == null) throw new BusinessException("建档期就业人数必填");
        if (r.getCurrentCount() == null) throw new BusinessException("调查期就业人数必填");
        validateNumbers(r);
        if (r.getCurrentCount() < r.getBaseCount()) {
            if (isBlank(r.getDecreaseType())) throw new BusinessException("调查期就业人数减少时，减少类型必填");
            if (isBlank(r.getPrimaryReason())) throw new BusinessException("调查期就业人数减少时，主要原因必填");
            if ("R99".equals(r.getPrimaryReason()) && isBlank(r.getPrimaryReasonText()))
                throw new BusinessException("主要原因为「其他」时，需要填写说明");
        }
        validateReasonOrderingAndUnique(r);
    }

    /**
     * Cross-field rules for the three reason slots (apply on submit and on save):
     *  - 第三原因 requires 次要原因 to be filled first;
     *  - 主要 / 次要 / 第三 must not duplicate each other.
     * (减少类型 vs 原因 are independent dimensions, so no constraint between them.)
     */
    private void validateReasonOrderingAndUnique(Report r) {
        boolean primary   = !isBlank(r.getPrimaryReason());
        boolean secondary = !isBlank(r.getSecondaryReason());
        boolean third     = !isBlank(r.getThirdReason());
        if (third && !secondary) {
            throw new BusinessException("填写第三原因前，请先填写次要原因");
        }
        if (primary && secondary && r.getPrimaryReason().equals(r.getSecondaryReason())) {
            throw new BusinessException("次要原因不能与主要原因相同");
        }
        if (primary && third && r.getPrimaryReason().equals(r.getThirdReason())) {
            throw new BusinessException("第三原因不能与主要原因相同");
        }
        if (secondary && third && r.getSecondaryReason().equals(r.getThirdReason())) {
            throw new BusinessException("第三原因不能与次要原因相同");
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String trim(String s) { return s == null ? null : s.trim(); }
    private static String now() { return LocalDateTime.now().format(FMT); }

    // =====================================================================
    //  M3 — City review methods
    // =====================================================================

    private static final Set<String> CITY_VISIBLE_STATUSES = Set.of(
            Report.STATUS_CITY_PEND, Report.STATUS_CITY_OK, Report.STATUS_CITY_RETURN,
            Report.STATUS_PROV_PEND, Report.STATUS_PROV_OK, Report.STATUS_PROV_RETURN, Report.STATUS_SUBMITTED);

    /** List reports for a city user; scoped to enterprises in their region. */
    public List<CityReviewVO> listForCity(String regionCode, String status, Long periodId, String keyword) {
        // Step 1 — find enterprises in the region (with optional name/orgCode keyword)
        LambdaQueryWrapper<EnterpriseInfo> entQ = Wrappers.<EnterpriseInfo>lambdaQuery()
                .eq(EnterpriseInfo::getRegionCode, regionCode);
        if (keyword != null && !keyword.isBlank()) {
            entQ.and(w -> w.like(EnterpriseInfo::getName, keyword)
                    .or().like(EnterpriseInfo::getOrgCode, keyword));
        }
        List<EnterpriseInfo> enterprises = enterpriseInfoMapper.selectList(entQ);
        if (enterprises.isEmpty()) return List.of();
        Map<Long, EnterpriseInfo> entMap = enterprises.stream()
                .collect(Collectors.toMap(EnterpriseInfo::getId, e -> e));

        // Step 2 — fetch reports
        LambdaQueryWrapper<Report> q = Wrappers.<Report>lambdaQuery()
                .in(Report::getEnterpriseId, entMap.keySet());
        if (status != null && !status.isBlank()) q.eq(Report::getStatus, status);
        else q.in(Report::getStatus, CITY_VISIBLE_STATUSES);
        if (periodId != null) q.eq(Report::getPeriodId, periodId);
        q.orderByDesc(Report::getSubmittedAt);
        List<Report> reports = mapper.selectList(q);

        // Step 3 — enrich
        return reports.stream().map(r -> enrich(r, entMap.get(r.getEnterpriseId()), periodService.getById(r.getPeriodId()))).toList();
    }

    public CityReviewVO cityDetail(String regionCode, Long reportId) {
        Report r = mapper.selectById(reportId);
        if (r == null) throw new BusinessException("数据不存在");
        EnterpriseInfo ei = enterpriseInfoMapper.selectById(r.getEnterpriseId());
        if (ei == null || !regionCode.equals(ei.getRegionCode()))
            throw new BusinessException(403, "无权查看");
        return enrich(r, ei, periodService.getById(r.getPeriodId()));
    }

    public Report cityApprove(String regionCode, String reviewerName, Long reportId) {
        Report r = loadCityActionable(regionCode, reportId, Report.STATUS_CITY_PEND, "仅「待市级审核」状态可审核通过");
        String at = now();
        r.setStatus(Report.STATUS_CITY_OK);
        r.setCityReviewer(reviewerName);
        r.setCityReviewAt(at);
        r.setCityReturnReason(null);
        r.setUpdatedAt(at);
        mapper.updateById(r);
        // Mirror onto current attempt (still open — passes to province later)
        ReportAttempt a = latestOpenAttempt(reportId);
        if (a != null) {
            a.setStatus(Report.STATUS_CITY_OK);
            a.setCityReviewer(reviewerName);
            a.setCityReviewAt(at);
            attemptMapper.updateById(a);
        }
        sysLog.log("CITY_APPROVE", "report:" + reportId, "市级审核通过 by " + reviewerName);
        return r;
    }

    public Report cityReturn(String regionCode, String reviewerName, Long reportId, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("退回原因不能为空");
        Report r = loadCityActionable(regionCode, reportId, Report.STATUS_CITY_PEND, "仅「待市级审核」状态可退回");
        String at = now();
        String trimmed = reason.trim();
        r.setStatus(Report.STATUS_CITY_RETURN);
        r.setCityReviewer(reviewerName);
        r.setCityReviewAt(at);
        r.setCityReturnReason(trimmed);
        r.setUpdatedAt(at);
        mapper.updateById(r);
        // Close the current attempt — a new one will be created on the next enterprise submit
        ReportAttempt a = latestOpenAttempt(reportId);
        if (a != null) {
            a.setStatus(Report.STATUS_CITY_RETURN);
            a.setCityReviewer(reviewerName);
            a.setCityReviewAt(at);
            a.setCityReturnReason(trimmed);
            a.setClosedAt(at);
            attemptMapper.updateById(a);
        }
        sysLog.log("CITY_RETURN", "report:" + reportId, "市级退回，原因：" + trimmed);
        return r;
    }

    public Map<String, Object> cityBatchApprove(String regionCode, String reviewerName, List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择至少一条记录");
        int success = 0;
        List<Map<String, Object>> failures = new java.util.ArrayList<>();
        for (Long id : ids) {
            try {
                cityApprove(regionCode, reviewerName, id);
                success++;
            } catch (BusinessException ex) {
                failures.add(Map.of("id", id, "reason", ex.getMessage()));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", ids.size());
        result.put("success", success);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    public Map<String, Object> citySubmitToProvince(String regionCode, String reviewerName, List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择至少一条记录");
        int success = 0;
        List<Map<String, Object>> failures = new java.util.ArrayList<>();
        for (Long id : ids) {
            try {
                Report r = loadCityActionable(regionCode, id, Report.STATUS_CITY_OK, "仅「市级已通过」状态可上报省级");
                String at = now();
                r.setStatus(Report.STATUS_PROV_PEND);
                r.setUpdatedAt(at);
                mapper.updateById(r);
                ReportAttempt a = latestOpenAttempt(id);
                if (a != null) {
                    a.setStatus(Report.STATUS_PROV_PEND);
                    attemptMapper.updateById(a);
                }
                sysLog.log("CITY_TO_PROVINCE", "report:" + id, "市级上报至省级");
                success++;
            } catch (BusinessException ex) {
                failures.add(Map.of("id", id, "reason", ex.getMessage()));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", ids.size());
        result.put("success", success);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    // =====================================================================
    //  Enterprise-side: list attempts for history view
    // =====================================================================

    public List<AttemptVO> listMyAttempts(Long userId, Long periodId, String status) {
        EnterpriseInfo ei = filingService.getByUserId(userId);
        if (ei == null) return List.of();
        // Filter out attempts whose underlying report has been (logically) deleted
        List<Long> liveReportIds = mapper.selectList(Wrappers.<Report>lambdaQuery()
                .eq(Report::getEnterpriseId, ei.getId())
                .select(Report::getId))
                .stream().map(Report::getId).toList();
        if (liveReportIds.isEmpty()) return List.of();
        LambdaQueryWrapper<ReportAttempt> q = Wrappers.<ReportAttempt>lambdaQuery()
                .eq(ReportAttempt::getEnterpriseId, ei.getId())
                .in(ReportAttempt::getReportId, liveReportIds)
                .eq(periodId != null, ReportAttempt::getPeriodId, periodId)
                .eq(status != null && !status.isEmpty(), ReportAttempt::getStatus, status)
                .orderByDesc(ReportAttempt::getSubmittedAt)
                .orderByDesc(ReportAttempt::getAttemptSeq);
        List<ReportAttempt> attempts = attemptMapper.selectList(q);
        return attempts.stream().map(a -> {
            Period p = periodService.getById(a.getPeriodId());
            AttemptVO vo = new AttemptVO();
            vo.setId(a.getId());
            vo.setReportId(a.getReportId());
            vo.setPeriodId(a.getPeriodId());
            vo.setPeriodName(p != null ? p.getName() : "");
            vo.setPeriodType(p != null ? p.getPeriodType() : "");
            vo.setAttemptSeq(a.getAttemptSeq());
            vo.setBaseCount(a.getBaseCount());
            vo.setCurrentCount(a.getCurrentCount());
            vo.setStatus(a.getStatus());
            vo.setSubmittedAt(a.getSubmittedAt());
            vo.setCityReviewAt(a.getCityReviewAt());
            vo.setCityReviewer(a.getCityReviewer());
            vo.setCityReturnReason(a.getCityReturnReason());
            vo.setProvReviewAt(a.getProvReviewAt());
            vo.setProvReviewer(a.getProvReviewer());
            vo.setProvReturnReason(a.getProvReturnReason());
            vo.setClosedAt(a.getClosedAt());
            return vo;
        }).toList();
    }

    // =====================================================================
    //  M4 — Province report management & utilities
    // =====================================================================

    private static final Set<String> PROV_VISIBLE_STATUSES = Set.of(
            Report.STATUS_PROV_PEND, Report.STATUS_PROV_OK, Report.STATUS_PROV_RETURN, Report.STATUS_SUBMITTED);

    /** List reports for province; no region scoping. */
    public List<CityReviewVO> listForProvince(String status, String regionCode, Long periodId, String keyword) {
        // Build enterprise filter (region/keyword)
        LambdaQueryWrapper<EnterpriseInfo> entQ = Wrappers.<EnterpriseInfo>lambdaQuery();
        if (regionCode != null && !regionCode.isBlank()) entQ.eq(EnterpriseInfo::getRegionCode, regionCode);
        if (keyword != null && !keyword.isBlank()) {
            entQ.and(w -> w.like(EnterpriseInfo::getName, keyword)
                    .or().like(EnterpriseInfo::getOrgCode, keyword));
        }
        List<EnterpriseInfo> enterprises = enterpriseInfoMapper.selectList(entQ);
        if (enterprises.isEmpty()) return List.of();
        Map<Long, EnterpriseInfo> entMap = enterprises.stream()
                .collect(Collectors.toMap(EnterpriseInfo::getId, e -> e));

        LambdaQueryWrapper<Report> q = Wrappers.<Report>lambdaQuery()
                .in(Report::getEnterpriseId, entMap.keySet());
        if (status != null && !status.isBlank()) q.eq(Report::getStatus, status);
        else q.in(Report::getStatus, PROV_VISIBLE_STATUSES);
        if (periodId != null) q.eq(Report::getPeriodId, periodId);
        q.orderByDesc(Report::getSubmittedAt);
        List<Report> reports = mapper.selectList(q);

        return reports.stream().map(r -> enrich(r, entMap.get(r.getEnterpriseId()), periodService.getById(r.getPeriodId()))).toList();
    }

    public Report getRawReport(Long id) {
        Report r = mapper.selectById(id);
        if (r == null) throw new BusinessException("数据不存在");
        return r;
    }

    public Report provApprove(String reviewerName, Long reportId) {
        Report r = loadProvActionable(reportId, Report.STATUS_PROV_PEND, "仅「待省级审核」状态可审核通过");
        String at = now();
        r.setStatus(Report.STATUS_PROV_OK);
        r.setProvReviewer(reviewerName);
        r.setProvReviewAt(at);
        r.setProvReturnReason(null);
        r.setUpdatedAt(at);
        mapper.updateById(r);
        ReportAttempt a = latestOpenAttempt(reportId);
        if (a != null) {
            a.setStatus(Report.STATUS_PROV_OK);
            a.setProvReviewer(reviewerName);
            a.setProvReviewAt(at);
            attemptMapper.updateById(a);
        }
        sysLog.log("PROV_APPROVE", "report:" + reportId, "省级审核通过 by " + reviewerName);
        return r;
    }

    public Report provReturn(String reviewerName, Long reportId, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("退回原因不能为空");
        Report r = loadProvActionable(reportId, Report.STATUS_PROV_PEND, "仅「待省级审核」状态可退回");
        String at = now();
        String trimmed = reason.trim();
        r.setStatus(Report.STATUS_PROV_RETURN);
        r.setProvReviewer(reviewerName);
        r.setProvReviewAt(at);
        r.setProvReturnReason(trimmed);
        r.setUpdatedAt(at);
        mapper.updateById(r);
        ReportAttempt a = latestOpenAttempt(reportId);
        if (a != null) {
            a.setStatus(Report.STATUS_PROV_RETURN);
            a.setProvReviewer(reviewerName);
            a.setProvReviewAt(at);
            a.setProvReturnReason(trimmed);
            a.setClosedAt(at);
            attemptMapper.updateById(a);
        }
        sysLog.log("PROV_RETURN", "report:" + reportId, "省级退回，原因：" + trimmed);
        return r;
    }

    public Map<String, Object> provBatchSubmitNation(String reviewerName, List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择至少一条记录");
        int success = 0;
        List<Map<String, Object>> failures = new java.util.ArrayList<>();
        for (Long id : ids) {
            try {
                Report r = loadProvActionable(id, Report.STATUS_PROV_OK, "仅「省级已通过」状态可上报国家");
                String at = now();
                r.setStatus(Report.STATUS_SUBMITTED);
                r.setUpdatedAt(at);
                mapper.updateById(r);
                ReportAttempt a = latestOpenAttempt(id);
                if (a != null) {
                    a.setStatus(Report.STATUS_SUBMITTED);
                    a.setClosedAt(at);
                    attemptMapper.updateById(a);
                }
                success++;
            } catch (BusinessException ex) {
                failures.add(Map.of("id", id, "reason", ex.getMessage()));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", ids.size());
        result.put("success", success);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    public void deleteReport(Long reportId, String operatorName) {
        Report r = mapper.selectById(reportId);
        if (r == null) throw new BusinessException("数据不存在");
        if (Report.STATUS_SUBMITTED.equals(r.getStatus()))
            throw new BusinessException("数据已上报国家，不可删除");
        // soft delete via @TableLogic
        mapper.deleteById(reportId);
        sysLog.log("DELETE_REPORT", "report:" + reportId, "省级逻辑删除报表 by " + operatorName);
    }

    private Report loadProvActionable(Long reportId, String requiredStatus, String wrongStatusMsg) {
        Report r = mapper.selectById(reportId);
        if (r == null) throw new BusinessException("数据不存在");
        if (!requiredStatus.equals(r.getStatus()))
            throw new BusinessException(wrongStatusMsg + "（当前 " + r.getStatus() + "）");
        return r;
    }

    /** Used by aggregation: returns a copy of the report whose data fields are overlaid by latest revision (if any). */
    public Report effectiveReport(Report r, ReportRevision latestRevision) {
        if (latestRevision == null) return r;
        Report eff = new Report();
        // copy basic fields
        eff.setId(r.getId());
        eff.setEnterpriseId(r.getEnterpriseId());
        eff.setPeriodId(r.getPeriodId());
        eff.setStatus(r.getStatus());
        eff.setSubmittedAt(r.getSubmittedAt());
        // overlay revised data
        eff.setBaseCount(latestRevision.getBaseCount());
        eff.setCurrentCount(latestRevision.getCurrentCount());
        eff.setDecreaseType(latestRevision.getDecreaseType());
        eff.setPrimaryReason(latestRevision.getPrimaryReason());
        eff.setPrimaryReasonText(latestRevision.getPrimaryReasonText());
        eff.setSecondaryReason(latestRevision.getSecondaryReason());
        eff.setSecondaryReasonText(latestRevision.getSecondaryReasonText());
        eff.setThirdReason(latestRevision.getThirdReason());
        eff.setThirdReasonText(latestRevision.getThirdReasonText());
        eff.setOtherReason(latestRevision.getOtherReason());
        return eff;
    }

    public ReportMapper rawMapper() { return mapper; }
    public EnterpriseInfoMapper rawEnterpriseMapper() { return enterpriseInfoMapper; }
    public PeriodService rawPeriodService() { return periodService; }

    private Report loadCityActionable(String regionCode, Long reportId, String requiredStatus, String wrongStatusMsg) {
        Report r = mapper.selectById(reportId);
        if (r == null) throw new BusinessException("数据不存在");
        EnterpriseInfo ei = enterpriseInfoMapper.selectById(r.getEnterpriseId());
        if (ei == null || !regionCode.equals(ei.getRegionCode()))
            throw new BusinessException(403, "无权操作（不属于本辖区）");
        if (!requiredStatus.equals(r.getStatus()))
            throw new BusinessException(wrongStatusMsg + "（当前 " + r.getStatus() + "）");
        return r;
    }

    private CityReviewVO enrich(Report r, EnterpriseInfo ei, Period p) {
        CityReviewVO vo = new CityReviewVO();
        vo.setId(r.getId());
        vo.setEnterpriseId(r.getEnterpriseId());
        vo.setPeriodId(r.getPeriodId());
        if (ei != null) {
            vo.setEnterpriseName(ei.getName());
            vo.setOrgCode(ei.getOrgCode());
            vo.setRegionCode(ei.getRegionCode());
            vo.setRegionName(ei.getRegionName());
        }
        if (p != null) {
            vo.setPeriodName(p.getName());
            vo.setPeriodType(p.getPeriodType());
        }
        vo.setBaseCount(r.getBaseCount());
        vo.setCurrentCount(r.getCurrentCount());
        vo.setDecreaseType(r.getDecreaseType());
        vo.setPrimaryReason(r.getPrimaryReason());
        vo.setStatus(r.getStatus());
        vo.setSubmittedAt(r.getSubmittedAt());
        vo.setUpdatedAt(r.getUpdatedAt());
        vo.setCityReviewer(r.getCityReviewer());
        vo.setCityReviewAt(r.getCityReviewAt());
        vo.setCityReturnReason(r.getCityReturnReason());
        return vo;
    }
}
