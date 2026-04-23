package com.yn.employment.modules.system.period;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import com.yn.employment.modules.system.log.SysLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PeriodService {

    public static final String TYPE_HALF_MONTH = "HALF_MONTH";
    public static final String TYPE_MONTH = "MONTH";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    private final PeriodMapper mapper;
    @Autowired private SysLogService sysLog;

    public PeriodService(PeriodMapper mapper) { this.mapper = mapper; }

    public List<Period> listByYear(Integer year) {
        return mapper.selectList(Wrappers.<Period>lambdaQuery()
                .eq(year != null, Period::getYear, year)
                .orderByAsc(Period::getYear).orderByAsc(Period::getSeqInYear));
    }

    public List<Period> listOpen() {
        return mapper.selectList(Wrappers.<Period>lambdaQuery()
                .eq(Period::getStatus, STATUS_OPEN)
                .orderByDesc(Period::getYear).orderByDesc(Period::getSeqInYear));
    }

    public Period getById(Long id) {
        return mapper.selectById(id);
    }

    /** One-click generate 15 periods for a year: 6 half-month (Jan–Mar) + 9 month (Apr–Dec). */
    public int generateForYear(int year) {
        List<Period> exists = listByYear(year);
        if (!exists.isEmpty()) {
            throw new BusinessException(year + " 年已存在 " + exists.size() + " 个调查期，请先清理后再生成");
        }
        List<Period> all = buildYearPeriods(year);
        for (Period p : all) mapper.insert(p);
        log.info("Generated {} periods for year {}", all.size(), year);
        if (sysLog != null) sysLog.log("PERIOD_GENERATE", "year:" + year, "一键生成 " + all.size() + " 个调查期");
        return all.size();
    }

    public static List<Period> buildYearPeriods(int year) {
        List<Period> out = new ArrayList<>(15);
        int seq = 1;
        // Jan-Mar: half-month
        for (int month = 1; month <= 3; month++) {
            int lastDay = YearMonth.of(year, month).lengthOfMonth();
            out.add(makePeriod(year, month, 1, 15, seq++, TYPE_HALF_MONTH, "上半月"));
            out.add(makePeriod(year, month, 16, lastDay, seq++, TYPE_HALF_MONTH, "下半月"));
        }
        // Apr-Dec: full month
        for (int month = 4; month <= 12; month++) {
            int lastDay = YearMonth.of(year, month).lengthOfMonth();
            out.add(makePeriod(year, month, 1, lastDay, seq++, TYPE_MONTH, "月报"));
        }
        return out;
    }

    private static Period makePeriod(int year, int month, int sd, int ed, int seq, String type, String suffix) {
        Period p = new Period();
        p.setYear(year);
        p.setSeqInYear(seq);
        p.setPeriodType(type);
        p.setStartDate(String.format("%04d-%02d-%02d", year, month, sd));
        p.setEndDate(String.format("%04d-%02d-%02d", year, month, ed));
        p.setName(String.format("%d年%d月%s", year, month, suffix));
        p.setStatus(STATUS_OPEN);
        return p;
    }

    public void updateStatus(Long id, String status) {
        Period p = mapper.selectById(id);
        if (p == null) throw new BusinessException("调查期不存在");
        if (!STATUS_OPEN.equals(status) && !STATUS_CLOSED.equals(status)) {
            throw new BusinessException("非法状态");
        }
        p.setStatus(status);
        mapper.updateById(p);
        if (sysLog != null) sysLog.log("PERIOD_TOGGLE", "period:" + id, p.getName() + " 状态 → " + status);
    }

    public boolean ensureYearGenerated(int year) {
        if (listByYear(year).isEmpty()) {
            generateForYear(year);
            return true;
        }
        return false;
    }
}
