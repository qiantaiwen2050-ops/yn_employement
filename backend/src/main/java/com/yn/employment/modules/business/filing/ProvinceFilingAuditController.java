package com.yn.employment.modules.business.filing;

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

@RestController
@RequestMapping("/province/filing")
public class ProvinceFilingAuditController {

    private final FilingService service;

    public ProvinceFilingAuditController(FilingService service) { this.service = service; }

    @GetMapping
    public Result<List<EnterpriseInfo>> list(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String regionCode,
                                             @RequestParam(required = false) String keyword) {
        requireProvince();
        return Result.ok(service.listForProvince(status, regionCode, keyword));
    }

    @GetMapping("/{id}")
    public Result<EnterpriseInfo> detail(@PathVariable Long id) {
        requireProvince();
        EnterpriseInfo ei = service.getById(id);
        if (ei == null) throw new BusinessException("备案不存在");
        return Result.ok(ei);
    }

    @PostMapping("/{id}/approve")
    public Result<EnterpriseInfo> approve(@PathVariable Long id) {
        UserContext.CurrentUser u = requireProvince();
        return Result.ok(service.provinceApprove(id, reviewerName(u)));
    }

    @PostMapping("/{id}/reject")
    public Result<EnterpriseInfo> reject(@PathVariable Long id, @RequestBody ReasonRequest req) {
        UserContext.CurrentUser u = requireProvince();
        return Result.ok(service.provinceReject(id, reviewerName(u), req.getReason()));
    }

    @GetMapping("/export")
    public void exportXlsx(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String regionCode,
                           @RequestParam(required = false) String keyword,
                           HttpServletResponse resp) throws IOException {
        requireProvince();
        List<EnterpriseInfo> rows = service.listForProvince(status, regionCode, keyword);
        List<String> headers = List.of("企业名称", "组织机构代码", "所属地区", "企业性质", "所属行业",
                "联系人", "联系电话", "备案状态", "提交时间", "审核时间", "审核人", "退回原因");
        List<List<Object>> data = new ArrayList<>();
        for (EnterpriseInfo e : rows) {
            data.add(List.of(
                    n(e.getName()), n(e.getOrgCode()), n(e.getRegionName()),
                    n(e.getNature()), n(e.getIndustry()), n(e.getContact()),
                    n(e.getPhone()), n(e.getFilingStatus()),
                    n(e.getSubmittedAt()), n(e.getReviewedAt()), n(e.getReviewedBy()),
                    n(e.getRejectReason())));
        }
        XlsxWriter.write(resp, "企业备案-" + LocalDate.now() + ".xlsx", "企业备案", headers, data);
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

    @Data
    public static class ReasonRequest { private String reason; }
}
