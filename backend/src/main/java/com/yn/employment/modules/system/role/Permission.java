package com.yn.employment.modules.system.role;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Catalog of permission codes referenced by roles. The codes are presentational
 * — primary auth still uses sys_user.user_type — but they let role configuration
 * be meaningful for SRS compliance and future fine-grained gating.
 */
public final class Permission {
    private Permission() {}

    @Data @AllArgsConstructor
    public static class Item {
        private String code;
        private String name;
        private String group;
    }

    public static final List<Item> CATALOG = List.of(
            // Enterprise
            new Item("ent.filing.edit",   "企业备案维护",  "企业端"),
            new Item("ent.report.fill",   "调查期数据填报", "企业端"),
            new Item("ent.report.history","历史数据查询",  "企业端"),
            new Item("ent.notice.read",   "通知浏览",      "企业端"),

            // City
            new Item("city.review.audit", "数据审核",      "市级"),
            new Item("city.review.batch", "批量审核",      "市级"),
            new Item("city.review.return","退回修改",      "市级"),
            new Item("city.review.submit","上报省级",      "市级"),
            new Item("city.notice.publish","通知发布",     "市级"),

            // Province — management
            new Item("prov.filing.audit", "备案审核",      "省级管理"),
            new Item("prov.report.audit", "报表审核",      "省级管理"),
            new Item("prov.report.return","报表退回",      "省级管理"),
            new Item("prov.report.modify","数据修改(留痕)", "省级管理"),
            new Item("prov.report.delete","数据删除",      "省级管理"),
            new Item("prov.report.submit","上报国家",      "省级管理"),
            new Item("prov.aggregation",  "数据汇总",      "省级管理"),

            // Province — analysis
            new Item("prov.ana.sampling", "取样分析",      "省级分析"),
            new Item("prov.ana.multidim", "多维分析",      "省级分析"),
            new Item("prov.ana.compare",  "对比分析",      "省级分析"),
            new Item("prov.ana.trend",    "趋势分析",      "省级分析"),

            // System management
            new Item("sys.user.manage",   "用户管理",      "系统管理"),
            new Item("sys.role.manage",   "角色管理",      "系统管理"),
            new Item("sys.period.manage", "调查期管理",    "系统管理"),
            new Item("sys.monitor",       "系统监控",      "系统管理"),
            new Item("sys.notice.publish","通知发布(省级)","系统管理"),
            new Item("sys.nation.upload", "国家接口上报",  "系统管理")
    );

    // Builtin role codes
    public static final String ROLE_PROVINCE_ADMIN = "ROLE_PROV_ADMIN";
    public static final String ROLE_CITY = "ROLE_CITY";
    public static final String ROLE_ENTERPRISE = "ROLE_ENTERPRISE";

    // Default permission sets per builtin role
    public static List<String> defaultPermsFor(String roleCode) {
        return switch (roleCode) {
            case ROLE_PROVINCE_ADMIN -> CATALOG.stream()
                    .filter(p -> p.code.startsWith("prov.") || p.code.startsWith("sys."))
                    .map(p -> p.code).toList();
            case ROLE_CITY -> CATALOG.stream()
                    .filter(p -> p.code.startsWith("city."))
                    .map(p -> p.code).toList();
            case ROLE_ENTERPRISE -> CATALOG.stream()
                    .filter(p -> p.code.startsWith("ent."))
                    .map(p -> p.code).toList();
            default -> List.of();
        };
    }
}
