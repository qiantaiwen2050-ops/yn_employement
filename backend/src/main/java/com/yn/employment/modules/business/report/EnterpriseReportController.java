package com.yn.employment.modules.business.report;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/enterprise/report")
public class EnterpriseReportController {

    private final ReportService service;

    public EnterpriseReportController(ReportService service) { this.service = service; }

    @GetMapping
    public Result<List<ReportListVO>> list(@RequestParam(required = false) Long periodId,
                                           @RequestParam(required = false) String status) {
        return Result.ok(service.listMine(requireEnterprise().getId(), periodId, status));
    }

    /** History as per-submission attempts (one row per enterprise submit). */
    @GetMapping("/attempts")
    public Result<List<AttemptVO>> attempts(@RequestParam(required = false) Long periodId,
                                            @RequestParam(required = false) String status) {
        return Result.ok(service.listMyAttempts(requireEnterprise().getId(), periodId, status));
    }

    @GetMapping("/draft")
    public Result<Report> draft(@RequestParam Long periodId) {
        return Result.ok(service.getOrCreateDraft(requireEnterprise().getId(), periodId));
    }

    @GetMapping("/{id}")
    public Result<Report> detail(@PathVariable Long id) {
        return Result.ok(service.detail(requireEnterprise().getId(), id));
    }

    @PutMapping
    public Result<Report> save(@RequestBody Report body) {
        return Result.ok(service.saveDraft(requireEnterprise().getId(), body));
    }

    @PostMapping("/submit")
    public Result<Report> submit(@RequestBody Report body) {
        return Result.ok(service.submit(requireEnterprise().getId(), body));
    }

    private UserContext.CurrentUser requireEnterprise() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"enterprise".equals(u.getUserType())) throw new BusinessException(403, "仅企业用户可操作");
        return u;
    }
}
