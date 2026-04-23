package com.yn.employment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.config.PasswordConfig;
import com.yn.employment.modules.business.filing.EnterpriseInfo;
import com.yn.employment.modules.business.filing.EnterpriseInfoMapper;
import com.yn.employment.modules.business.notice.Notice;
import com.yn.employment.modules.business.notice.NoticeMapper;
import com.yn.employment.modules.business.report.Report;
import com.yn.employment.modules.business.report.ReportAttempt;
import com.yn.employment.modules.business.report.ReportAttemptMapper;
import com.yn.employment.modules.business.report.ReportMapper;
import com.yn.employment.modules.system.role.Permission;
import com.yn.employment.modules.system.role.Role;
import com.yn.employment.modules.system.role.RoleService;
import com.yn.employment.modules.system.period.Period;
import com.yn.employment.modules.system.period.PeriodService;
import com.yn.employment.modules.system.user.User;
import com.yn.employment.modules.system.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserService userService;
    private final PasswordConfig.PasswordEncoder pwd;
    private final PeriodService periodService;
    private final EnterpriseInfoMapper enterpriseInfoMapper;
    private final ReportMapper reportMapper;
    private final ReportAttemptMapper attemptMapper;
    private final NoticeMapper noticeMapper;
    private final RoleService roleService;

    public DataSeeder(UserService userService,
                      PasswordConfig.PasswordEncoder pwd,
                      PeriodService periodService,
                      EnterpriseInfoMapper enterpriseInfoMapper,
                      ReportMapper reportMapper,
                      ReportAttemptMapper attemptMapper,
                      NoticeMapper noticeMapper,
                      RoleService roleService) {
        this.userService = userService;
        this.pwd = pwd;
        this.periodService = periodService;
        this.enterpriseInfoMapper = enterpriseInfoMapper;
        this.reportMapper = reportMapper;
        this.attemptMapper = attemptMapper;
        this.noticeMapper = noticeMapper;
        this.roleService = roleService;
    }

    @Override
    public void run(String... args) {
        // M6: builtin roles must exist before users (so role assignments can be made)
        seedBuiltinRoles();

        User provUser = ensureUser("province", "省级管理员", "province", null, null);
        assignBuiltinRole(provUser, Permission.ROLE_PROVINCE_ADMIN);
        User cityUser = ensureUser("kunming", "昆明市人社局", "city", "530100", "昆明市");
        assignBuiltinRole(cityUser, Permission.ROLE_CITY);
        User qjUser   = ensureUser("qujing", "曲靖市人社局", "city", "530300", "曲靖市");
        assignBuiltinRole(qjUser, Permission.ROLE_CITY);
        User yxUser   = ensureUser("yuxi",   "玉溪市人社局", "city", "530400", "玉溪市");
        assignBuiltinRole(yxUser, Permission.ROLE_CITY);
        User entUser  = ensureUser("ent001", "演示企业(昆明)", "enterprise", "530100", "昆明市");
        assignBuiltinRole(entUser, Permission.ROLE_ENTERPRISE);
        User ent2User = ensureUser("ent002", "演示企业(曲靖)", "enterprise", "530300", "曲靖市");
        assignBuiltinRole(ent2User, Permission.ROLE_ENTERPRISE);

        // Generate 15 periods for the current year if not yet present
        int year = LocalDate.now().getYear();
        if (periodService.ensureYearGenerated(year)) {
            log.info("Seeded 15 survey periods for year {}", year);
        }

        // ent001 备案 APPROVED → can fill reports;  ent002 备案 PENDING → 省级 has something to audit
        EnterpriseInfo ei = ensureSampleEnterpriseFiling(entUser);
        ensurePendingEnterpriseFiling(ent2User);

        // Seed a few reports for ent001; mix of city-pending, province-pending, province-approved
        seedSampleReports(ei, year);

        // M5: seed extra approved enterprises across regions/natures/industries +
        // multiple periods of province-approved reports so analytics charts have content.
        seedAnalysisFixtures(year);

        // Seed one sample city notice
        seedSampleNotice(cityUser);
    }

    private User ensureUser(String username, String realName, String type, String region, String regionName) {
        User u = userService.getByUsername(username);
        if (u != null) return u;
        u = new User();
        u.setUsername(username);
        u.setPassword(pwd.encode("123456"));
        u.setRealName(realName);
        u.setUserType(type);
        u.setRegionCode(region);
        u.setRegionName(regionName);
        u.setStatus(1);
        userService.save(u);
        log.info("Seeded user: {} ({})", username, type);
        return userService.getByUsername(username);
    }

    private void ensurePendingEnterpriseFiling(User entUser) {
        EnterpriseInfo existing = enterpriseInfoMapper.selectOne(
                Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, entUser.getId()));
        if (existing != null) return;
        EnterpriseInfo ei = new EnterpriseInfo();
        ei.setUserId(entUser.getId());
        ei.setRegionCode(entUser.getRegionCode());
        ei.setRegionName(entUser.getRegionName());
        ei.setOrgCode("87654321Y");
        ei.setName("演示企业(曲靖)实业有限公司");
        ei.setNature("STATE");
        ei.setIndustry("F");
        ei.setMainBusiness("商贸物流与仓储服务");
        ei.setContact("李主管");
        ei.setAddress("云南省曲靖市麒麟区南宁西路 88 号");
        ei.setPostcode("655000");
        ei.setPhone("0874-31000000");
        ei.setFax("0874-31000001");
        ei.setEmail("hr@demo.qj.example.com");
        ei.setFilingStatus(EnterpriseInfo.STATUS_PENDING);
        String now = LocalDateTime.now().format(FMT);
        ei.setSubmittedAt(now);
        enterpriseInfoMapper.insert(ei);
        log.info("Seeded PENDING filing for {}", entUser.getUsername());
    }

    private EnterpriseInfo ensureSampleEnterpriseFiling(User entUser) {
        EnterpriseInfo existing = enterpriseInfoMapper.selectOne(
                Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, entUser.getId()));
        if (existing != null) return existing;
        EnterpriseInfo ei = new EnterpriseInfo();
        ei.setUserId(entUser.getId());
        ei.setRegionCode(entUser.getRegionCode());
        ei.setRegionName(entUser.getRegionName());
        ei.setOrgCode("12345678X");
        ei.setName("演示企业(昆明)有限公司");
        ei.setNature("PRIVATE");
        ei.setIndustry("C");
        ei.setMainBusiness("通用机械设备制造、销售与售后服务");
        ei.setContact("张经理");
        ei.setAddress("云南省昆明市五华区青年路 168 号");
        ei.setPostcode("650000");
        ei.setPhone("0871-65000000");
        ei.setFax("0871-65000001");
        ei.setEmail("contact@demo.example.com");
        ei.setFilingStatus(EnterpriseInfo.STATUS_APPROVED);
        String now = LocalDateTime.now().format(FMT);
        ei.setSubmittedAt(now);
        ei.setReviewedAt(now);
        ei.setReviewedBy("province");
        enterpriseInfoMapper.insert(ei);
        log.info("Seeded sample enterprise filing for {}", entUser.getUsername());
        return enterpriseInfoMapper.selectOne(
                Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, entUser.getId()));
    }

    /** Seed 4 reports across the workflow stages so each role demo has content immediately. */
    private void seedSampleReports(EnterpriseInfo ei, int year) {
        Long existing = reportMapper.selectCount(Wrappers.<Report>lambdaQuery().eq(Report::getEnterpriseId, ei.getId()));
        if (existing != null && existing > 0) return;

        List<Period> periods = periodService.listByYear(year);
        if (periods.size() < 4) return;
        String now = LocalDateTime.now().format(FMT);
        String reviewer = "昆明市人社局";
        String provReviewer = "省级管理员";

        // 1月上半月 — 01 (待市级审核): for city demo
        insertReport(ei.getId(), periods.get(0).getId(), 100, 105, null, null, null,
                Report.STATUS_CITY_PEND, null, null, null, null, now);
        // 1月下半月 — 01 (待市级审核): for city demo (decrease + reason)
        insertReport(ei.getId(), periods.get(1).getId(), 105, 98, "03", "R04", "订单淡季",
                Report.STATUS_CITY_PEND, null, null, null, null, now);
        // 2月上半月 — 04 (待省级审核): already through city, ready for province demo
        insertReport(ei.getId(), periods.get(2).getId(), 100, 100, null, null, null,
                Report.STATUS_PROV_PEND, reviewer, now, null, null, now);
        // 2月下半月 — 05 (省级已通过): ready for nation-submit + aggregation demo
        insertReport(ei.getId(), periods.get(3).getId(), 110, 108, "06", "R11", "员工正常离职",
                Report.STATUS_PROV_OK, reviewer, now, provReviewer, now, now);

        log.info("Seeded 4 sample reports for enterprise id={}", ei.getId());
    }

    private void insertReport(Long entId, Long periodId, int base, int curr,
                              String decreaseType, String primaryReason, String primaryReasonText,
                              String status, String cityReviewer, String cityReviewAt,
                              String provReviewer, String provReviewAt, String now) {
        Report r = new Report();
        r.setEnterpriseId(entId);
        r.setPeriodId(periodId);
        r.setBaseCount(base);
        r.setCurrentCount(curr);
        r.setDecreaseType(decreaseType);
        r.setPrimaryReason(primaryReason);
        r.setPrimaryReasonText(primaryReasonText);
        r.setStatus(status);
        r.setCityReviewer(cityReviewer);
        r.setCityReviewAt(cityReviewAt);
        r.setProvReviewer(provReviewer);
        r.setProvReviewAt(provReviewAt);
        r.setSubmittedAt(now);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        reportMapper.insert(r);
        // Mirror as the first attempt
        ReportAttempt a = new ReportAttempt();
        a.setReportId(r.getId());
        a.setEnterpriseId(entId);
        a.setPeriodId(periodId);
        a.setAttemptSeq(1);
        a.setBaseCount(base);
        a.setCurrentCount(curr);
        a.setDecreaseType(decreaseType);
        a.setPrimaryReason(primaryReason);
        a.setPrimaryReasonText(primaryReasonText);
        a.setSubmittedAt(now);
        a.setStatus(status);
        a.setCityReviewer(cityReviewer);
        a.setCityReviewAt(cityReviewAt);
        a.setProvReviewer(provReviewer);
        a.setProvReviewAt(provReviewAt);
        attemptMapper.insert(a);
    }

    /** Create extra APPROVED enterprises across regions + a span of approved reports for analytics. */
    private void seedAnalysisFixtures(int year) {
        // Skip if we already have more than the original 2 enterprises
        Long n = enterpriseInfoMapper.selectCount(null);
        if (n != null && n > 2) return;

        record Spec(String username, String real, String region, String regionName, String orgCode, String name, String nature, String industry) {}
        List<Spec> specs = List.of(
                new Spec("ent003", "玉溪企业A", "530400", "玉溪市", "30000003A", "玉溪烟草配套有限公司",   "STATE",   "C"),
                new Spec("ent004", "玉溪企业B", "530400", "玉溪市", "30000004A", "玉溪汇商贸易有限公司",   "PRIVATE", "F"),
                new Spec("ent005", "曲靖企业A", "530300", "曲靖市", "30000005A", "曲靖煤业集团有限公司",   "STATE",   "B"),
                new Spec("ent006", "昆明企业B", "530100", "昆明市", "30000006A", "昆明软件信息有限公司",   "PRIVATE", "I"),
                new Spec("ent007", "昆明企业C", "530100", "昆明市", "30000007A", "昆明教育服务有限公司",   "COLLECTIVE","P"),
                new Spec("ent008", "保山企业A", "530500", "保山市", "30000008A", "保山农业科技有限公司",   "PRIVATE", "A"),
                new Spec("ent009", "昭通企业A", "530600", "昭通市", "30000009A", "昭通批发零售有限公司",   "FOREIGN", "F")
        );

        String now = LocalDateTime.now().format(FMT);
        List<Period> periods = periodService.listByYear(year);
        if (periods.size() < 7) return;

        // Use first 6 periods (1月上半月 .. 3月下半月: all HALF_MONTH) and 4月..7月 (MONTH).
        // Trends within a single period_type need ≥3 sequential — half-month gives us 6, month gives 4-7 = 4. Good.

        for (Spec sp : specs) {
            User u = ensureUser(sp.username, sp.real, "enterprise", sp.region, sp.regionName);
            EnterpriseInfo ei = enterpriseInfoMapper.selectOne(
                    Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, u.getId()));
            if (ei == null) {
                ei = new EnterpriseInfo();
                ei.setUserId(u.getId());
                ei.setRegionCode(sp.region);
                ei.setRegionName(sp.regionName);
                ei.setOrgCode(sp.orgCode);
                ei.setName(sp.name);
                ei.setNature(sp.nature);
                ei.setIndustry(sp.industry);
                ei.setMainBusiness("演示用经营业务说明");
                ei.setContact("联系人");
                ei.setAddress(sp.regionName + " 演示地址");
                ei.setPostcode("650000");
                ei.setPhone("0871-99999999");
                ei.setFax("0871-99999998");
                ei.setEmail("demo@example.com");
                ei.setFilingStatus(EnterpriseInfo.STATUS_APPROVED);
                ei.setSubmittedAt(now);
                ei.setReviewedAt(now);
                ei.setReviewedBy("province");
                enterpriseInfoMapper.insert(ei);
                ei = enterpriseInfoMapper.selectOne(Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, u.getId()));
            }

            // Seed a province-approved report for each of the first 6 (half-month) periods + first 4 monthly periods.
            int seed = (int) (ei.getId() * 7L);  // deterministic per-enterprise variation
            int base = 80 + (seed % 40);
            for (int i = 0; i < Math.min(10, periods.size()); i++) {
                Period p = periods.get(i);
                int delta = ((seed + i * 13) % 11) - 5;   // -5..+5 fluctuation
                int curr = Math.max(0, base + delta);
                String dt = null, pr = null, prText = null;
                if (curr < base) {
                    dt = "03"; pr = "R04"; prText = "订单变化";
                }
                insertReport(ei.getId(), p.getId(), base, curr, dt, pr, prText,
                        Report.STATUS_PROV_OK, "市级人社局", now, "省级管理员", now, now);
            }
        }
        log.info("Seeded {} extra enterprises with approved reports for analytics", specs.size());
    }

    private void seedBuiltinRoles() {
        ensureBuiltinRole(Permission.ROLE_PROVINCE_ADMIN, "省级管理员", "省级用户：备案审核 / 报表管理 / 汇总 / 分析 / 系统管理 / 国家上报");
        ensureBuiltinRole(Permission.ROLE_CITY,           "市级管理员", "市级用户：辖区数据审核 / 上报省级 / 通知发布");
        ensureBuiltinRole(Permission.ROLE_ENTERPRISE,     "企业用户",   "企业用户：备案 / 月度填报 / 历史查询 / 通知浏览");
    }

    private void ensureBuiltinRole(String code, String name, String desc) {
        Role r = roleService.getByCode(code);
        if (r != null) return;
        r = roleService.insertBuiltin(code, name, desc);
        roleService.savePerms(r.getId(), Permission.defaultPermsFor(code));
        log.info("Seeded builtin role: {}", code);
    }

    private void assignBuiltinRole(User u, String roleCode) {
        Role r = roleService.getByCode(roleCode);
        if (r == null) return;
        if (!roleService.roleIdsForUser(u.getId()).contains(r.getId())) {
            // append; don't blow away other assignments
            java.util.List<Long> existing = new java.util.ArrayList<>(roleService.roleIdsForUser(u.getId()));
            existing.add(r.getId());
            roleService.assignRolesToUser(u.getId(), existing);
        }
    }

    private void seedSampleNotice(User cityUser) {
        Long existing = noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery().eq(Notice::getPublisherId, cityUser.getId()));
        if (existing != null && existing > 0) return;
        Notice n = new Notice();
        n.setTitle("昆明市企业就业失业月度数据填报提醒");
        n.setContent("各企业请于本月 10 日前完成上月就业失业数据填报。如有疑问请联系市人社局监测科。\n\n联系电话：0871-12345678");
        n.setPublisherId(cityUser.getId());
        n.setPublisherUsername(cityUser.getUsername());
        n.setPublisherRealName(cityUser.getRealName());
        n.setPublisherType("city");
        n.setPublisherRegionCode(cityUser.getRegionCode());
        n.setPublisherRegionName(cityUser.getRegionName());
        String now = LocalDateTime.now().format(FMT);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        noticeMapper.insert(n);
        log.info("Seeded sample city notice from {}", cityUser.getUsername());
    }
}
