package com.yn.employment.modules.business.report;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/city/review")
public class CityReviewController {

    private final ReportService service;

    public CityReviewController(ReportService service) { this.service = service; }

    @GetMapping
    public Result<List<CityReviewVO>> list(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long periodId,
                                           @RequestParam(required = false) String keyword) {
        UserContext.CurrentUser u = requireCity();
        return Result.ok(service.listForCity(u.getRegionCode(), status, periodId, keyword));
    }

    @GetMapping("/{id}")
    public Result<CityReviewVO> detail(@PathVariable Long id) {
        UserContext.CurrentUser u = requireCity();
        return Result.ok(service.cityDetail(u.getRegionCode(), id));
    }

    @PostMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id) {
        UserContext.CurrentUser u = requireCity();
        service.cityApprove(u.getRegionCode(), reviewerName(u), id);
        return Result.ok();
    }

    @PostMapping("/{id}/return")
    public Result<?> ret(@PathVariable Long id, @RequestBody ReturnRequest req) {
        UserContext.CurrentUser u = requireCity();
        service.cityReturn(u.getRegionCode(), reviewerName(u), id, req.getReason());
        return Result.ok();
    }

    @PostMapping("/batch-approve")
    public Result<Map<String, Object>> batchApprove(@RequestBody IdsRequest req) {
        UserContext.CurrentUser u = requireCity();
        return Result.ok(service.cityBatchApprove(u.getRegionCode(), reviewerName(u), req.getIds()));
    }

    @PostMapping("/submit-province")
    public Result<Map<String, Object>> submitToProvince(@RequestBody IdsRequest req) {
        UserContext.CurrentUser u = requireCity();
        return Result.ok(service.citySubmitToProvince(u.getRegionCode(), reviewerName(u), req.getIds()));
    }

    private UserContext.CurrentUser requireCity() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"city".equals(u.getUserType())) throw new BusinessException(403, "仅市级用户可操作");
        if (u.getRegionCode() == null || u.getRegionCode().isBlank())
            throw new BusinessException(403, "当前账号未配置辖区");
        return u;
    }

    private String reviewerName(UserContext.CurrentUser u) {
        return u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername();
    }

    @Data
    public static class ReturnRequest {
        private String reason;
    }

    @Data
    public static class IdsRequest {
        private List<Long> ids;
    }
}
