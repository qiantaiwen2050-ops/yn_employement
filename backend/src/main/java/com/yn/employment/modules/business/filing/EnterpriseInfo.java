package com.yn.employment.modules.business.filing;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("enterprise_info")
public class EnterpriseInfo {
    private Long id;
    private Long userId;
    private String regionCode;
    private String regionName;
    private String orgCode;
    private String name;
    private String nature;
    private String industry;
    private String mainBusiness;
    private String contact;
    private String address;
    private String postcode;
    private String phone;
    private String fax;
    private String email;
    private String filingStatus;          // DRAFT | PENDING | APPROVED | REJECTED
    @TableField(updateStrategy = FieldStrategy.IGNORED)   // allow explicit null on approve
    private String rejectReason;
    private String submittedAt;
    private String reviewedAt;
    private String reviewedBy;
    private String createdAt;
    private String updatedAt;

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
}
