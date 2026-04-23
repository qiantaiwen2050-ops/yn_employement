package com.yn.employment.modules.business.analysis;

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
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    /** Reports that count toward analytics — only province-final or nation-submitted. */
    private static final Set<String> EFFECTIVE_STATUSES = Set.of(
            Report.STATUS_PROV_OK, Report.STATUS_SUBMITTED);

    public enum Dimension { region, nature, industry }

    private final ReportService reportService;
    private final ReportRevisionService revisionService;
    private final EnterpriseInfoMapper enterpriseInfoMapper;
    private final PeriodService periodService;
    private final DictItemMapper dictMapper;

    public AnalysisService(ReportService reportService, ReportRevisionService revisionService,
                           EnterpriseInfoMapper enterpriseInfoMapper, PeriodService periodService,
                           DictItemMapper dictMapper) {
        this.reportService = reportService;
        this.revisionService = revisionService;
        this.enterpriseInfoMapper = enterpriseInfoMapper;
        this.periodService = periodService;
        this.dictMapper = dictMapper;
    }

    // =====================================================================
    //  F-ANA-01 取样分析 — 各市企业数及占比
    // =====================================================================

    public SamplingResult sampling(String regionCode) {
        var entQ = Wrappers.<EnterpriseInfo>lambdaQuery()
                .eq(EnterpriseInfo::getFilingStatus, EnterpriseInfo.STATUS_APPROVED);
        if (regionCode != null && !regionCode.isBlank()) entQ.eq(EnterpriseInfo::getRegionCode, regionCode);
        List<EnterpriseInfo> entList = enterpriseInfoMapper.selectList(entQ);
        long total = entList.size();

        Map<String, String> regionMap = loadDictMap("REGION");
        Map<String, Long> countByRegion = entList.stream()
                .collect(Collectors.groupingBy(e -> orDash(e.getRegionCode()), Collectors.counting()));

        List<SamplingRow> rows = countByRegion.entrySet().stream()
                .map(en -> {
                    String code = en.getKey();
                    long count = en.getValue();
                    double pct = total == 0 ? 0.0 : count * 100.0 / total;
                    return new SamplingRow(code, regionMap.getOrDefault(code, code), count, round2(pct));
                })
                .sorted(Comparator.comparingLong(SamplingRow::getCount).reversed())
                .collect(Collectors.toList());
        // Add rank
        int rank = 1;
        for (SamplingRow r : rows) r.setRank(rank++);

        return new SamplingResult(regionCode == null || regionCode.isBlank() ? "全省" : regionMap.getOrDefault(regionCode, regionCode),
                total, rows);
    }

    // =====================================================================
    //  F-ANA-02 多维分析 — 1~3 维交叉
    // =====================================================================

    public MultidimResult multidim(Long periodId, List<String> dimensionCodes) {
        if (periodId == null) throw new BusinessException("调查期必填");
        if (dimensionCodes == null || dimensionCodes.isEmpty()) throw new BusinessException("请至少选择 1 个维度");
        if (dimensionCodes.size() > 3) throw new BusinessException("最多支持 3 个维度");
        Period p = periodService.getById(periodId);
        if (p == null) throw new BusinessException("调查期不存在");
        List<Dimension> dims = dimensionCodes.stream().map(s -> {
            try { return Dimension.valueOf(s); }
            catch (Exception e) { throw new BusinessException("非法维度: " + s); }
        }).toList();

        List<EnterpriseInfo> ents = enterpriseInfoMapper.selectList(null);
        Map<Long, EnterpriseInfo> entMap = ents.stream()
                .collect(Collectors.toMap(EnterpriseInfo::getId, e -> e, (a, b) -> a));

        List<Report> reports = reportService.rawMapper().selectList(Wrappers.<Report>lambdaQuery()
                .eq(Report::getPeriodId, periodId)
                .in(Report::getStatus, EFFECTIVE_STATUSES));

        // Build (groupKey, group rows) where groupKey = list of dim values
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        Map<String, List<String>> groupValues = new HashMap<>();
        Map<Dimension, Map<String, String>> dictByDim = new HashMap<>();
        for (Dimension d : dims) dictByDim.put(d, loadDictMap(dictTypeFor(d)));

        for (Report r : reports) {
            Report eff = reportService.effectiveReport(r, revisionService.latestForReport(r.getId()));
            EnterpriseInfo e = entMap.get(r.getEnterpriseId());
            List<String> values = dims.stream().map(d -> codeFor(e, d)).toList();
            String key = String.join("\u0001", values);
            groupValues.putIfAbsent(key, values);
            groups.computeIfAbsent(key, k -> new GroupAccumulator()).add(eff);
        }

        List<MultidimRow> rows = groups.entrySet().stream().map(en -> {
            GroupAccumulator g = en.getValue();
            List<String> vals = groupValues.get(en.getKey());
            List<String> labels = new ArrayList<>(dims.size());
            for (int i = 0; i < dims.size(); i++) {
                String code = vals.get(i);
                labels.add(dictByDim.get(dims.get(i)).getOrDefault(code, code));
            }
            double pct = g.baseTotal == 0 ? 0.0 : (g.currentTotal - g.baseTotal) * 100.0 / g.baseTotal;
            return new MultidimRow(vals, labels, g.entCount(), g.baseTotal, g.currentTotal,
                    g.currentTotal - g.baseTotal, g.decreaseTotal, round2(pct));
        }).sorted(Comparator.comparing(MultidimRow::getKey)).collect(Collectors.toList());

        List<String> dimNames = dims.stream().map(this::dimensionName).toList();
        return new MultidimResult(p.getName(), dimensionCodes, dimNames, rows);
    }

    // =====================================================================
    //  F-ANA-03 对比分析 — 两期 × 单维度
    // =====================================================================

    public CompareResult compare(Long periodIdA, Long periodIdB, String dimensionCode) {
        if (periodIdA == null || periodIdB == null) throw new BusinessException("请选择 2 个调查期");
        if (periodIdA.equals(periodIdB)) throw new BusinessException("请选择 2 个不同的调查期");
        Period pa = periodService.getById(periodIdA);
        Period pb = periodService.getById(periodIdB);
        if (pa == null || pb == null) throw new BusinessException("调查期不存在");
        if (!Objects.equals(pa.getPeriodType(), pb.getPeriodType()))
            throw new BusinessException("两个调查期的周期类型必须相同（半月报只能与半月报比较）");
        // ensure A is the earlier one for stable display
        if (pa.getYear() > pb.getYear() || (pa.getYear().equals(pb.getYear()) && pa.getSeqInYear() > pb.getSeqInYear())) {
            Period tmp = pa; pa = pb; pb = tmp;
            Long t = periodIdA; periodIdA = periodIdB; periodIdB = t;
        }
        Dimension dim = parseDim(dimensionCode);

        Map<String, GroupAccumulator> aGroups = aggregateByDim(periodIdA, dim);
        Map<String, GroupAccumulator> bGroups = aggregateByDim(periodIdB, dim);
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(aGroups.keySet());
        allKeys.addAll(bGroups.keySet());

        Map<String, String> dimDict = loadDictMap(dictTypeFor(dim));
        List<CompareRow> rows = allKeys.stream().map(k -> {
            GroupAccumulator a = aGroups.getOrDefault(k, GroupAccumulator.empty());
            GroupAccumulator b = bGroups.getOrDefault(k, GroupAccumulator.empty());
            long delta = b.currentTotal - a.currentTotal;
            String rate;
            if (a.currentTotal == 0) rate = "—";
            else rate = round2(delta * 100.0 / a.currentTotal) + "%";
            return new CompareRow(k, dimDict.getOrDefault(k, k),
                    a.entCount(), a.baseTotal, a.currentTotal, a.decreaseTotal,
                    b.entCount(), b.baseTotal, b.currentTotal, b.decreaseTotal,
                    delta, rate);
        }).collect(Collectors.toList());

        return new CompareResult(pa.getName(), pb.getName(), dimensionCode, dimensionName(dim), rows);
    }

    // =====================================================================
    //  F-ANA-04 趋势分析 — ≥3 连续期 × 维度（或全省）
    // =====================================================================

    public TrendResult trend(List<Long> periodIds, String dimensionCode) {
        if (periodIds == null || periodIds.size() < 3) throw new BusinessException("请选择 3 个及以上连续调查期");
        List<Period> periods = periodIds.stream().map(periodService::getById).toList();
        for (Period p : periods) if (p == null) throw new BusinessException("调查期不存在");
        // sort ascending
        List<Period> sorted = periods.stream()
                .sorted(Comparator.<Period, Integer>comparing(Period::getYear).thenComparing(Period::getSeqInYear))
                .toList();
        // validate same period_type
        String type = sorted.get(0).getPeriodType();
        for (Period p : sorted) if (!type.equals(p.getPeriodType()))
            throw new BusinessException("所选调查期周期类型必须一致（半月报或月报，二选一）");
        // validate sequential within same year (simpler than cross-year)
        for (int i = 1; i < sorted.size(); i++) {
            Period prev = sorted.get(i - 1), cur = sorted.get(i);
            if (!Objects.equals(prev.getYear(), cur.getYear()) || cur.getSeqInYear() != prev.getSeqInYear() + 1)
                throw new BusinessException("调查期必须连续（同年内 seq_in_year 相邻）");
        }

        Dimension dim = dimensionCode == null || dimensionCode.isBlank() ? null : parseDim(dimensionCode);

        // For each period, aggregate by dim (or one bucket "全省" if dim is null)
        List<Map<String, GroupAccumulator>> perPeriod = sorted.stream()
                .map(p -> dim == null ? Map.of("ALL", aggregateAll(p.getId())) : aggregateByDim(p.getId(), dim))
                .toList();
        Set<String> allKeys = new TreeSet<>();
        for (Map<String, GroupAccumulator> m : perPeriod) allKeys.addAll(m.keySet());

        Map<String, String> dimDict = dim == null ? Map.of("ALL", "全省") : loadDictMap(dictTypeFor(dim));

        List<TrendSeries> series = allKeys.stream().map(key -> {
            List<Double> changePcts = perPeriod.stream().map(m -> {
                GroupAccumulator g = m.getOrDefault(key, GroupAccumulator.empty());
                if (g.baseTotal == 0) return 0.0;
                return round2((g.currentTotal - g.baseTotal) * 100.0 / g.baseTotal);
            }).toList();
            List<Long> currents = perPeriod.stream().map(m -> m.getOrDefault(key, GroupAccumulator.empty()).currentTotal).toList();
            List<Long> bases = perPeriod.stream().map(m -> m.getOrDefault(key, GroupAccumulator.empty()).baseTotal).toList();
            return new TrendSeries(key, dimDict.getOrDefault(key, key), changePcts, currents, bases);
        }).collect(Collectors.toList());

        List<String> periodNames = sorted.stream().map(Period::getName).toList();
        return new TrendResult(periodNames, dim == null ? "全省" : dimensionName(dim), series);
    }

    // ---------------------------------------------------------------------

    private Map<String, GroupAccumulator> aggregateByDim(Long periodId, Dimension dim) {
        List<Report> reports = reportService.rawMapper().selectList(Wrappers.<Report>lambdaQuery()
                .eq(Report::getPeriodId, periodId)
                .in(Report::getStatus, EFFECTIVE_STATUSES));
        if (reports.isEmpty()) return Map.of();
        Set<Long> entIds = reports.stream().map(Report::getEnterpriseId).collect(Collectors.toSet());
        Map<Long, EnterpriseInfo> entMap = enterpriseInfoMapper.selectList(Wrappers.<EnterpriseInfo>lambdaQuery()
                .in(EnterpriseInfo::getId, entIds)).stream()
                .collect(Collectors.toMap(EnterpriseInfo::getId, e -> e));
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        for (Report r : reports) {
            Report eff = reportService.effectiveReport(r, revisionService.latestForReport(r.getId()));
            EnterpriseInfo e = entMap.get(r.getEnterpriseId());
            String key = codeFor(e, dim);
            groups.computeIfAbsent(key, k -> new GroupAccumulator()).add(eff);
        }
        return groups;
    }

    private GroupAccumulator aggregateAll(Long periodId) {
        Map<String, GroupAccumulator> g = aggregateByDim(periodId, Dimension.region);
        GroupAccumulator total = new GroupAccumulator();
        for (GroupAccumulator a : g.values()) total.merge(a);
        return total;
    }

    private Dimension parseDim(String code) {
        try { return Dimension.valueOf(code); }
        catch (Exception e) { throw new BusinessException("非法维度: " + code); }
    }

    private String codeFor(EnterpriseInfo e, Dimension d) {
        if (e == null) return "—";
        return switch (d) {
            case region   -> orDash(e.getRegionCode());
            case nature   -> orDash(e.getNature());
            case industry -> orDash(e.getIndustry());
        };
    }

    private String dictTypeFor(Dimension d) {
        return switch (d) {
            case region   -> "REGION";
            case nature   -> "ENT_NATURE";
            case industry -> "INDUSTRY";
        };
    }

    private String dimensionName(Dimension d) {
        return switch (d) {
            case region   -> "地区";
            case nature   -> "企业性质";
            case industry -> "所属行业";
        };
    }

    private Map<String, String> loadDictMap(String type) {
        return dictMapper.selectList(Wrappers.<DictItem>lambdaQuery().eq(DictItem::getDictType, type))
                .stream().collect(Collectors.toMap(DictItem::getItemCode, DictItem::getItemName, (a, b) -> a));
    }

    private static String orDash(String s) { return (s == null || s.isBlank()) ? "—" : s; }
    private static double round2(double d) { return Math.round(d * 100.0) / 100.0; }

    // ===== Accumulator & response types =====

    static class GroupAccumulator {
        Set<Long> enterpriseIds = new HashSet<>();
        long baseTotal = 0;
        long currentTotal = 0;
        long decreaseTotal = 0;

        static GroupAccumulator empty() { return new GroupAccumulator(); }

        void add(Report eff) {
            enterpriseIds.add(eff.getEnterpriseId());
            int b = eff.getBaseCount() == null ? 0 : eff.getBaseCount();
            int c = eff.getCurrentCount() == null ? 0 : eff.getCurrentCount();
            baseTotal += b;
            currentTotal += c;
            decreaseTotal += Math.max(0, b - c);
        }

        void merge(GroupAccumulator o) {
            enterpriseIds.addAll(o.enterpriseIds);
            baseTotal += o.baseTotal;
            currentTotal += o.currentTotal;
            decreaseTotal += o.decreaseTotal;
        }

        long entCount() { return enterpriseIds.size(); }
    }

    @Data @AllArgsConstructor
    public static class SamplingResult {
        private String scopeName;
        private long totalEnterprises;
        private List<SamplingRow> rows;
    }

    @Data @AllArgsConstructor
    public static class SamplingRow {
        private String regionCode;
        private String regionName;
        private long count;
        private double percent;
        private int rank;
        public SamplingRow(String regionCode, String regionName, long count, double percent) {
            this(regionCode, regionName, count, percent, 0);
        }
    }

    @Data @AllArgsConstructor
    public static class MultidimResult {
        private String periodName;
        private List<String> dimensionCodes;  // e.g. ["region", "nature"]
        private List<String> dimensionNames;  // e.g. ["地区", "企业性质"]
        private List<MultidimRow> rows;
    }

    @Data @AllArgsConstructor
    public static class MultidimRow {
        private List<String> dimensionValueCodes;
        private List<String> dimensionValueLabels;
        private long enterpriseCount;
        private long baseCount;
        private long currentCount;
        private long change;
        private long decreaseCount;
        private double changePct;
        public String getKey() { return String.join("/", dimensionValueCodes); }
    }

    @Data @AllArgsConstructor
    public static class CompareResult {
        private String periodAName;
        private String periodBName;
        private String dimensionCode;
        private String dimensionName;
        private List<CompareRow> rows;
    }

    @Data @AllArgsConstructor
    public static class CompareRow {
        private String code;
        private String name;
        private long entCountA;
        private long baseCountA;
        private long currentCountA;
        private long decreaseCountA;
        private long entCountB;
        private long baseCountB;
        private long currentCountB;
        private long decreaseCountB;
        private long delta;
        private String changeRatePct;
    }

    @Data @AllArgsConstructor
    public static class TrendResult {
        private List<String> periodNames;
        private String dimensionName;
        private List<TrendSeries> series;
    }

    @Data @AllArgsConstructor
    public static class TrendSeries {
        private String code;
        private String name;
        private List<Double> changePcts;
        private List<Long> currentCounts;
        private List<Long> baseCounts;
    }
}
