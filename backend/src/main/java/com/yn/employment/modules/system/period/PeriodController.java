package com.yn.employment.modules.system.period;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/period")
public class PeriodController {

    private final PeriodService service;

    public PeriodController(PeriodService service) { this.service = service; }

    /** Anyone logged-in can list periods (used by enterprise to pick a period). */
    @GetMapping
    public Result<List<Period>> list(@RequestParam(required = false) Integer year) {
        return Result.ok(service.listByYear(year));
    }

    @GetMapping("/open")
    public Result<List<Period>> listOpen() {
        return Result.ok(service.listOpen());
    }

    /** Province-only management endpoints. */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestParam int year) {
        requireProvince();
        int n = service.generateForYear(year);
        return Result.ok(Map.of("year", year, "generated", n));
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestParam String status) {
        requireProvince();
        service.updateStatus(id, status);
        return Result.ok();
    }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
    }
}
