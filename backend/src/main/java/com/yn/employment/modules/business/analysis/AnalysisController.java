package com.yn.employment.modules.business.analysis;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import com.yn.employment.common.io.XlsxWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/province/analysis")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) { this.service = service; }

    @GetMapping("/sampling")
    public Result<AnalysisService.SamplingResult> sampling(@RequestParam(required = false) String regionCode) {
        requireProvince();
        return Result.ok(service.sampling(regionCode));
    }

    @GetMapping("/multidim")
    public Result<AnalysisService.MultidimResult> multidim(@RequestParam Long periodId,
                                                           @RequestParam String dimensions) {
        requireProvince();
        return Result.ok(service.multidim(periodId, Arrays.asList(dimensions.split(","))));
    }

    @GetMapping("/compare")
    public Result<AnalysisService.CompareResult> compare(@RequestParam Long periodIdA,
                                                         @RequestParam Long periodIdB,
                                                         @RequestParam String dimension) {
        requireProvince();
        return Result.ok(service.compare(periodIdA, periodIdB, dimension));
    }

    @GetMapping("/trend")
    public Result<AnalysisService.TrendResult> trend(@RequestParam String periodIds,
                                                     @RequestParam(required = false) String dimension) {
        requireProvince();
        List<Long> ids = Arrays.stream(periodIds.split(",")).map(String::trim).map(Long::valueOf).toList();
        return Result.ok(service.trend(ids, dimension));
    }

    // ===== XLSX exports =====

    @GetMapping("/sampling/export")
    public void exportSampling(@RequestParam(required = false) String regionCode, HttpServletResponse resp) throws IOException {
        requireProvince();
        AnalysisService.SamplingResult r = service.sampling(regionCode);
        List<String> headers = List.of("地区编码", "地区名称", "企业数", "占比(%)", "排名");
        List<List<Object>> data = new ArrayList<>();
        for (var row : r.getRows()) {
            data.add(List.of(n(row.getRegionCode()), n(row.getRegionName()),
                    row.getCount(), row.getPercent(), row.getRank()));
        }
        XlsxWriter.write(resp, "取样分析-" + r.getScopeName() + "-" + LocalDate.now() + ".xlsx", "取样分析", headers, data);
    }

    @GetMapping("/multidim/export")
    public void exportMultidim(@RequestParam Long periodId, @RequestParam String dimensions, HttpServletResponse resp) throws IOException {
        requireProvince();
        AnalysisService.MultidimResult r = service.multidim(periodId, Arrays.asList(dimensions.split(",")));
        List<String> headers = new ArrayList<>(r.getDimensionNames());
        headers.addAll(List.of("企业数", "建档期总人数", "调查期总人数", "变化数", "减少数", "变化率(%)"));
        List<List<Object>> data = new ArrayList<>();
        for (var row : r.getRows()) {
            List<Object> rowData = new ArrayList<>(row.getDimensionValueLabels());
            rowData.add(row.getEnterpriseCount());
            rowData.add(row.getBaseCount());
            rowData.add(row.getCurrentCount());
            rowData.add(row.getChange());
            rowData.add(row.getDecreaseCount());
            rowData.add(row.getChangePct());
            data.add(rowData);
        }
        XlsxWriter.write(resp, "多维分析-" + r.getPeriodName() + "-" + dimensions + "-" + LocalDate.now() + ".xlsx",
                "多维分析", headers, data);
    }

    @GetMapping("/compare/export")
    public void exportCompare(@RequestParam Long periodIdA, @RequestParam Long periodIdB,
                              @RequestParam String dimension, HttpServletResponse resp) throws IOException {
        requireProvince();
        AnalysisService.CompareResult r = service.compare(periodIdA, periodIdB, dimension);
        List<String> headers = List.of(
                r.getDimensionName() + "编码", r.getDimensionName() + "名称",
                r.getPeriodAName() + " 企业数", r.getPeriodAName() + " 建档期", r.getPeriodAName() + " 调查期", r.getPeriodAName() + " 减少数",
                r.getPeriodBName() + " 企业数", r.getPeriodBName() + " 建档期", r.getPeriodBName() + " 调查期", r.getPeriodBName() + " 减少数",
                "调查期人数差值", "变化率");
        List<List<Object>> data = new ArrayList<>();
        for (var row : r.getRows()) {
            data.add(List.of(n(row.getCode()), n(row.getName()),
                    row.getEntCountA(), row.getBaseCountA(), row.getCurrentCountA(), row.getDecreaseCountA(),
                    row.getEntCountB(), row.getBaseCountB(), row.getCurrentCountB(), row.getDecreaseCountB(),
                    row.getDelta(), n(row.getChangeRatePct())));
        }
        XlsxWriter.write(resp, "对比分析-" + r.getPeriodAName() + "_vs_" + r.getPeriodBName() + "-" + dimension + "-" + LocalDate.now() + ".xlsx",
                "对比分析", headers, data);
    }

    @GetMapping("/trend/export")
    public void exportTrend(@RequestParam String periodIds, @RequestParam(required = false) String dimension,
                            HttpServletResponse resp) throws IOException {
        requireProvince();
        List<Long> ids = Arrays.stream(periodIds.split(",")).map(String::trim).map(Long::valueOf).toList();
        AnalysisService.TrendResult r = service.trend(ids, dimension);
        List<String> headers = new ArrayList<>();
        headers.add(r.getDimensionName() + "编码");
        headers.add(r.getDimensionName() + "名称");
        for (String pn : r.getPeriodNames()) headers.add(pn + " 变化率(%)");
        List<List<Object>> data = new ArrayList<>();
        for (var s : r.getSeries()) {
            List<Object> rowData = new ArrayList<>();
            rowData.add(n(s.getCode()));
            rowData.add(n(s.getName()));
            for (Double d : s.getChangePcts()) rowData.add(d);
            data.add(rowData);
        }
        XlsxWriter.write(resp, "趋势分析-" + r.getDimensionName() + "-" + LocalDate.now() + ".xlsx",
                "趋势分析", headers, data);
    }

    private static String n(String s) { return s == null ? "" : s; }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
    }
}
