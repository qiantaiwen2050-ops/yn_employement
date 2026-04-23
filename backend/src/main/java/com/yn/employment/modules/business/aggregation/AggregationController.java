package com.yn.employment.modules.business.aggregation;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import com.yn.employment.common.io.XlsxWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/province/aggregation")
public class AggregationController {

    private final AggregationService service;

    public AggregationController(AggregationService service) { this.service = service; }

    @GetMapping
    public Result<AggregationService.AggregationResult> aggregate(@RequestParam Long periodId,
                                                                  @RequestParam(required = false) String dimension) {
        requireProvince();
        AggregationService.Dimension dim = dimension == null ? AggregationService.Dimension.region
                : AggregationService.Dimension.valueOf(dimension);
        return Result.ok(service.aggregate(periodId, dim));
    }

    @GetMapping("/export")
    public void exportXlsx(@RequestParam Long periodId,
                           @RequestParam(required = false) String dimension,
                           HttpServletResponse resp) throws IOException {
        requireProvince();
        AggregationService.Dimension dim = dimension == null ? AggregationService.Dimension.region
                : AggregationService.Dimension.valueOf(dimension);
        AggregationService.AggregationResult r = service.aggregate(periodId, dim);
        String filename = "汇总-" + r.getPeriodName() + "-" + dim + "-" + LocalDate.now() + ".xlsx";
        List<String> headers = List.of("分组", "名称", "企业数", "建档期总人数", "调查期总人数", "变化数", "减少数", "变化率(%)");
        List<List<Object>> data = new ArrayList<>();
        for (AggregationService.GroupRow g : r.getGroups()) {
            data.add(List.of(
                    n(g.getCode()), n(g.getName()),
                    g.getEnterpriseCount(), g.getBaseCount(), g.getCurrentCount(),
                    g.getChange(), g.getDecreaseCount(), g.getChangePct()));
        }
        XlsxWriter.write(resp, filename, "汇总-" + r.getPeriodName(), headers, data);
    }

    private static String n(String s) { return s == null ? "" : s; }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
    }
}
