package com.yn.employment.modules.business.report;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import com.yn.employment.common.io.XlsxWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/province/report")
public class ProvinceReportController {

    private final ReportService service;
    private final ReportRevisionService revisionService;
    private final com.yn.employment.modules.business.nationapi.NationReportService nationService;

    public ProvinceReportController(ReportService service, ReportRevisionService revisionService,
                                    com.yn.employment.modules.business.nationapi.NationReportService nationService) {
        this.service = service;
        this.revisionService = revisionService;
        this.nationService = nationService;
    }

    @GetMapping
    public Result<List<CityReviewVO>> list(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String regionCode,
                                           @RequestParam(required = false) Long periodId,
                                           @RequestParam(required = false) String keyword) {
        requireProvince();
        return Result.ok(service.listForProvince(status, regionCode, periodId, keyword));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        requireProvince();
        Report raw = service.getRawReport(id);
        ReportRevision latest = revisionService.latestForReport(id);
        Report effective = service.effectiveReport(raw, latest);
        return Result.ok(Map.of(
                "raw", raw,
                "effective", effective,
                "hasRevision", latest != null,
                "revisionCount", revisionService.listForReport(id).size()
        ));
    }

    @PostMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id) {
        UserContext.CurrentUser u = requireProvince();
        service.provApprove(reviewerName(u), id);
        return Result.ok();
    }

    @PostMapping("/{id}/return")
    public Result<?> ret(@PathVariable Long id, @RequestBody ReasonRequest req) {
        UserContext.CurrentUser u = requireProvince();
        service.provReturn(reviewerName(u), id, req.getReason());
        return Result.ok();
    }

    @PostMapping("/submit-nation")
    public Result<Map<String, Object>> submitNation(@RequestBody IdsRequest req) {
        requireProvince();
        // Routes through the nation API service so each (period, batch) gets a log row
        // and goes through the mock — same code path as the M6 「国家接口」 page.
        return Result.ok(nationService.uploadByReportIds(req.getIds()));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        UserContext.CurrentUser u = requireProvince();
        service.deleteReport(id, reviewerName(u));
        return Result.ok();
    }

    @PostMapping("/{id}/revise")
    public Result<ReportRevision> revise(@PathVariable Long id, @RequestBody ReportRevisionService.ReviseRequest req) {
        requireProvince();
        return Result.ok(revisionService.revise(id, req));
    }

    @GetMapping("/{id}/revisions")
    public Result<List<ReportRevision>> revisions(@PathVariable Long id) {
        requireProvince();
        return Result.ok(revisionService.listForReport(id));
    }

    @GetMapping("/export")
    public void exportXlsx(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String regionCode,
                           @RequestParam(required = false) Long periodId,
                           @RequestParam(required = false) String keyword,
                           HttpServletResponse resp) throws IOException {
        requireProvince();
        List<CityReviewVO> rows = service.listForProvince(status, regionCode, periodId, keyword);
        List<String> headers = List.of("企业", "组织机构代码", "所属地区", "调查期", "周期类型",
                "建档期", "调查期人数", "变化", "状态", "上报时间");
        List<List<Object>> data = new ArrayList<>();
        for (CityReviewVO r : rows) {
            int delta = (r.getCurrentCount() == null || r.getBaseCount() == null) ? 0
                    : r.getCurrentCount() - r.getBaseCount();
            data.add(List.of(
                    n(r.getEnterpriseName()), n(r.getOrgCode()), n(r.getRegionName()),
                    n(r.getPeriodName()), n(r.getPeriodType()),
                    r.getBaseCount() == null ? "" : r.getBaseCount(),
                    r.getCurrentCount() == null ? "" : r.getCurrentCount(),
                    delta,
                    n(r.getStatus()), n(r.getSubmittedAt())));
        }
        XlsxWriter.write(resp, "省级报表-" + LocalDate.now() + ".xlsx", "省级报表", headers, data);
    }

    private static String n(String s) { return s == null ? "" : s; }

    private UserContext.CurrentUser requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
        return u;
    }

    private String reviewerName(UserContext.CurrentUser u) {
        return u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername();
    }

    @Data public static class ReasonRequest { private String reason; }
    @Data public static class IdsRequest { private List<Long> ids; }
}
