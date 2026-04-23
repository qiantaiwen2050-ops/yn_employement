package com.yn.employment.modules.business.nationapi;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/province/nation")
public class NationReportController {

    private final NationReportService service;

    public NationReportController(NationReportService service) { this.service = service; }

    @PostMapping("/upload")
    public Result<NationReportLog> upload(@RequestParam Long periodId) {
        requireProvince();
        return Result.ok(service.uploadPeriod(periodId));
    }

    @PostMapping("/retry/{logId}")
    public Result<NationReportLog> retry(@PathVariable Long logId) {
        requireProvince();
        return Result.ok(service.retry(logId));
    }

    @GetMapping("/log")
    public Result<List<NationReportLog>> log(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String reportType,
                                             @RequestParam(required = false) Long periodId) {
        requireProvince();
        return Result.ok(service.listLogs(status, reportType, periodId));
    }

    @GetMapping("/log/{id}")
    public Result<NationReportLog> detail(@PathVariable Long id) {
        requireProvince();
        return Result.ok(service.getLog(id));
    }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
    }
}
