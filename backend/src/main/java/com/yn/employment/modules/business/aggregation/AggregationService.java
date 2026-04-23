package com.yn.employment.modules.business.aggregation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import com.yn.employment.modules.business.filing.EnterpriseInfo;
import com.yn.employment.modules.business.filing.EnterpriseInfoMapper;
import com.yn.employment.modules.business.report.Report;
import com.yn.employment.modules.business.report.ReportRevisionService;
import com.yn.employment.modules.business.report.ReportService;
import com.yn.employment.modules.system.dict.DictItem;
import com.yn.employment.modules.system.dict.DictItemMapper;
import com.yn.employment.modules.system.period.Period;
import com.yn.employment.modules.system.period.PeriodService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AggregationService {

    private static final Set<String> EFFECTIVE_STATUSES = Set.of(
            Report.STATUS_PROV_OK, Report.STATUS_SUBMITTED);

    public enum Dimension { region, nature, industry }

    private final ReportService reportService;
    private final ReportRevisionService revisionService;
    private final EnterpriseInfoMapper enterpriseInfoMapper;
    private final PeriodService periodService;
    private final DictItemMapper dictMapper;

    public AggregationService(ReportService reportService, ReportRevisionService revisionService,
                              EnterpriseInfoMapper enterpriseInfoMapper, PeriodService periodService,
                              DictItemMapper dictMapper) {
        this.reportService = reportService;
        this.revisionService = revisionService;
        this.enterpriseInfoMapper = enterpriseInfoMapper;
        this.periodService = periodService;
        this.dictMapper = dictMapper;
    }

    /** Aggregate effective report values for a single period, optionally grouped by dimension. */
    public AggregationResult aggregate(Long periodId, Dimension dimension) {
        if (periodId == null) throw new BusinessException("调查期必填");
        Period p = periodService.getById(periodId);
        if (p == null) throw new BusinessException("调查期不存在");

        // Use ReportService.listForProvince with no filter to fetch all reports for this period
        // — we want submitted + approved (any non-deleted, status >= 04). Easier to fetch directly.
        List<Report> reports = reportService.rawMapper().selectList(Wrappers.<Report>lambdaQuery()
                .eq(Report::getPeriodId, periodId));
        if (reports.isEmpty()) {
            return new AggregationResult(p.getName(), 0, 0, 0, 0, List.of());
        }

        // Pull all relevant enterprises in one go
        Set<Long> entIds = reports.stream().map(Report::getEnterpriseId).collect(Collectors.toSet());
        Map<Long, EnterpriseInfo> entMap = enterpriseInfoMapper.selectList(Wrappers.<EnterpriseInfo>lambdaQuery()
                .in(EnterpriseInfo::getId, entIds)).stream()
                .collect(Collectors.toMap(EnterpriseInfo::getId, e -> e));

        // Use effective values where revision exists
        List<EffectiveRow> effective = reports.stream().map(r -> {
            Report eff = reportService.effectiveReport(r, revisionService.latestForReport(r.getId()));
            EnterpriseInfo e = entMap.get(r.getEnterpriseId());
            return new EffectiveRow(eff, e);
        }).toList();

        // Top-line metrics computed only over "effective" data (province-approved or submitted)
        List<EffectiveRow> effectiveOnly = effective.stream()
                .filter(e -> EFFECTIVE_STATUSES.contains(e.report.getStatus()))
                .toList();

        long totalEnt = effectiveOnly.stream().map(e -> e.report.getEnterpriseId()).distinct().count();
        long totalBase = effectiveOnly.stream().mapToLong(e -> safeLong(e.report.getBaseCount())).sum();
        long totalCurrent = effectiveOnly.stream().mapToLong(e -> safeLong(e.report.getCurrentCount())).sum();
        long totalDecrease = effectiveOnly.stream().mapToLong(e -> {
            int b = safeInt(e.report.getBaseCount()), c = safeInt(e.report.getCurrentCount());
            return Math.max(0, b - c);
        }).sum();

        // Group by dimension (only effective rows participate in groups)
        Dimension dim = dimension == null ? Dimension.region : dimension;
        Map<String, String> codeToName = loadDictMap(dim);

        Map<String, List<EffectiveRow>> groups = effectiveOnly.stream()
                .collect(Collectors.groupingBy(e -> dimensionCode(e, dim)));

        List<GroupRow> rows = groups.entrySet().stream().map(en -> {
            String code = en.getKey();
            List<EffectiveRow> grp = en.getValue();
            long entCount = grp.stream().map(g -> g.report.getEnterpriseId()).distinct().count();
            long base = grp.stream().mapToLong(g -> safeLong(g.report.getBaseCount())).sum();
            long curr = grp.stream().mapToLong(g -> safeLong(g.report.getCurrentCount())).sum();
            long decrease = grp.stream().mapToLong(g -> {
                int b = safeInt(g.report.getBaseCount()), c = safeInt(g.report.getCurrentCount());
                return Math.max(0, b - c);
            }).sum();
            double pct = base == 0 ? 0.0 : (curr - base) * 100.0 / base;
            return new GroupRow(code, codeToName.getOrDefault(code, code), entCount, base, curr, curr - base, decrease, round2(pct));
        }).sorted(Comparator.comparing(GroupRow::getCode)).toList();

        return new AggregationResult(p.getName(), totalEnt, totalBase, totalCurrent, totalDecrease, rows);
    }

    private String dimensionCode(EffectiveRow e, Dimension dim) {
        if (e.enterprise == null) return "—";
        return switch (dim) {
            case region   -> orDash(e.enterprise.getRegionCode());
            case nature   -> orDash(e.enterprise.getNature());
            case industry -> orDash(e.enterprise.getIndustry());
        };
    }

    private Map<String, String> loadDictMap(Dimension dim) {
        String type = switch (dim) {
            case region   -> "REGION";
            case nature   -> "ENT_NATURE";
            case industry -> "INDUSTRY";
        };
        return dictMapper.selectList(Wrappers.<DictItem>lambdaQuery().eq(DictItem::getDictType, type))
                .stream().collect(Collectors.toMap(DictItem::getItemCode, DictItem::getItemName, (a, b) -> a));
    }

    private static String orDash(String s) { return (s == null || s.isBlank()) ? "—" : s; }
    private static long safeLong(Integer v) { return v == null ? 0 : v.longValue(); }
    private static int safeInt(Integer v) { return v == null ? 0 : v; }
    private static double round2(double d) { return Math.round(d * 100.0) / 100.0; }

    private static class EffectiveRow {
        final Report report;
        final EnterpriseInfo enterprise;
        EffectiveRow(Report r, EnterpriseInfo e) { this.report = r; this.enterprise = e; }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AggregationResult {
        private String periodName;
        private long totalEnterprises;
        private long totalBase;
        private long totalCurrent;
        private long totalDecrease;
        private List<GroupRow> groups;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GroupRow {
        private String code;
        private String name;
        private long enterpriseCount;
        private long baseCount;
        private long currentCount;
        private long change;
        private long decreaseCount;
        private double changePct;
    }
}
