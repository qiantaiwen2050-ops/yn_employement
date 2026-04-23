package com.yn.employment.modules.business.filing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import com.yn.employment.common.UserContext;
import com.yn.employment.modules.system.log.SysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class FilingService {

    private static final Pattern P_ORG_CODE = Pattern.compile("^[A-Za-z0-9]{1,9}$");
    private static final Pattern P_POSTCODE = Pattern.compile("^\\d{6}$");
    private static final Pattern P_PHONE    = Pattern.compile("^(\\d{3,4}[- ]?\\d{7,8}|1\\d{10})$");
    private static final Pattern P_FAX      = Pattern.compile("^\\d{3,4}[- ]?\\d{7,8}$");
    private static final Pattern P_EMAIL    = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EnterpriseInfoMapper mapper;
    @Autowired private SysLogService sysLog;   // field-injected to avoid touching ctor signature

    public FilingService(EnterpriseInfoMapper mapper) { this.mapper = mapper; }

    public EnterpriseInfo getMine() {
        Long userId = UserContext.require().getId();
        EnterpriseInfo ei = mapper.selectOne(Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, userId));
        if (ei == null) {
            // First time — auto-create empty draft so the form can render.
            UserContext.CurrentUser u = UserContext.require();
            ei = new EnterpriseInfo();
            ei.setUserId(userId);
            ei.setRegionCode(u.getRegionCode());
            ei.setRegionName(u.getRegionName());
            ei.setName(u.getRealName());
            ei.setFilingStatus(EnterpriseInfo.STATUS_DRAFT);
            mapper.insert(ei);
        }
        return ei;
    }

    public EnterpriseInfo getById(Long id) { return mapper.selectById(id); }

    /** Save without submitting — keeps current status (DRAFT or REJECTED stays editable). */
    public EnterpriseInfo save(EnterpriseInfo dto) {
        EnterpriseInfo cur = getMine();
        if (EnterpriseInfo.STATUS_PENDING.equals(cur.getFilingStatus())) {
            throw new BusinessException("当前备案正在审核中，不可修改");
        }
        applyEditable(cur, dto);
        cur.setUpdatedAt(LocalDateTime.now().format(FMT));
        mapper.updateById(cur);
        return cur;
    }

    /** Validate fully then submit for province review. */
    public EnterpriseInfo submit(EnterpriseInfo dto) {
        EnterpriseInfo cur = save(dto);
        validateRequired(cur);
        cur.setFilingStatus(EnterpriseInfo.STATUS_PENDING);
        cur.setRejectReason(null);
        cur.setSubmittedAt(LocalDateTime.now().format(FMT));
        cur.setUpdatedAt(cur.getSubmittedAt());
        mapper.updateById(cur);
        sysLog.log("SUBMIT_FILING", "filing:" + cur.getId(), "企业提交备案：" + cur.getName());
        return cur;
    }

    private void applyEditable(EnterpriseInfo cur, EnterpriseInfo dto) {
        // region_code/name come from the user account, not editable here (per SRS)
        cur.setOrgCode(trim(dto.getOrgCode()));
        cur.setName(trim(dto.getName()));
        cur.setNature(trim(dto.getNature()));
        cur.setIndustry(trim(dto.getIndustry()));
        cur.setMainBusiness(trim(dto.getMainBusiness()));
        cur.setContact(trim(dto.getContact()));
        cur.setAddress(trim(dto.getAddress()));
        cur.setPostcode(trim(dto.getPostcode()));
        cur.setPhone(trim(dto.getPhone()));
        cur.setFax(trim(dto.getFax()));
        cur.setEmail(trim(dto.getEmail()));
    }

    private void validateRequired(EnterpriseInfo ei) {
        require("组织机构代码", ei.getOrgCode());
        if (!P_ORG_CODE.matcher(ei.getOrgCode()).matches())
            throw new BusinessException("组织机构代码格式不正确（仅字母数字、不超过9位）");
        require("企业名称", ei.getName());
        require("企业性质", ei.getNature());
        require("所属行业", ei.getIndustry());
        require("主要经营业务", ei.getMainBusiness());
        require("联系人", ei.getContact());
        require("联系地址", ei.getAddress());
        require("邮政编码", ei.getPostcode());
        if (!P_POSTCODE.matcher(ei.getPostcode()).matches())
            throw new BusinessException("邮政编码必须为 6 位数字");
        require("联系电话", ei.getPhone());
        if (!P_PHONE.matcher(ei.getPhone()).matches())
            throw new BusinessException("联系电话格式不正确（区号-电话 或 11位手机号）");
        require("传真", ei.getFax());
        if (!P_FAX.matcher(ei.getFax()).matches())
            throw new BusinessException("传真格式不正确（区号-电话）");
        if (ei.getEmail() != null && !ei.getEmail().isEmpty()
                && !P_EMAIL.matcher(ei.getEmail()).matches())
            throw new BusinessException("电子邮箱格式不正确");
    }

    private void require(String label, String v) {
        if (v == null || v.isBlank()) throw new BusinessException(label + " 不能为空");
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }

    public boolean isApproved(Long userId) {
        EnterpriseInfo ei = mapper.selectOne(Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, userId));
        return ei != null && EnterpriseInfo.STATUS_APPROVED.equals(ei.getFilingStatus());
    }

    public EnterpriseInfo getByUserId(Long userId) {
        return mapper.selectOne(Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, userId));
    }

    // =====================================================================
    //  M4 — Province filing audit
    // =====================================================================

    public List<EnterpriseInfo> listForProvince(String status, String regionCode, String keyword) {
        LambdaQueryWrapper<EnterpriseInfo> q = Wrappers.<EnterpriseInfo>lambdaQuery()
                .ne(EnterpriseInfo::getFilingStatus, EnterpriseInfo.STATUS_DRAFT)
                .eq(status != null && !status.isBlank(), EnterpriseInfo::getFilingStatus, status)
                .eq(regionCode != null && !regionCode.isBlank(), EnterpriseInfo::getRegionCode, regionCode)
                .orderByDesc(EnterpriseInfo::getSubmittedAt);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(EnterpriseInfo::getName, keyword)
                    .or().like(EnterpriseInfo::getOrgCode, keyword));
        }
        return mapper.selectList(q);
    }

    public EnterpriseInfo provinceApprove(Long filingId, String reviewerName) {
        EnterpriseInfo ei = requireSubmitted(filingId);
        ei.setFilingStatus(EnterpriseInfo.STATUS_APPROVED);
        ei.setRejectReason(null);
        ei.setReviewedAt(LocalDateTime.now().format(FMT));
        ei.setReviewedBy(reviewerName);
        ei.setUpdatedAt(ei.getReviewedAt());
        mapper.updateById(ei);
        sysLog.log("APPROVE_FILING", "filing:" + filingId, "省级通过备案：" + ei.getName());
        return ei;
    }

    public EnterpriseInfo provinceReject(Long filingId, String reviewerName, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("退回原因不能为空");
        EnterpriseInfo ei = requireSubmitted(filingId);
        ei.setFilingStatus(EnterpriseInfo.STATUS_REJECTED);
        ei.setRejectReason(reason.trim());
        ei.setReviewedAt(LocalDateTime.now().format(FMT));
        ei.setReviewedBy(reviewerName);
        ei.setUpdatedAt(ei.getReviewedAt());
        mapper.updateById(ei);
        sysLog.log("REJECT_FILING", "filing:" + filingId, "省级退回备案：" + ei.getName() + "，原因：" + reason);
        return ei;
    }

    private EnterpriseInfo requireSubmitted(Long id) {
        EnterpriseInfo ei = mapper.selectById(id);
        if (ei == null) throw new BusinessException("备案信息不存在");
        if (!EnterpriseInfo.STATUS_PENDING.equals(ei.getFilingStatus())
                && !EnterpriseInfo.STATUS_REJECTED.equals(ei.getFilingStatus())
                && !EnterpriseInfo.STATUS_APPROVED.equals(ei.getFilingStatus())) {
            throw new BusinessException("当前状态不可操作");
        }
        if (EnterpriseInfo.STATUS_PENDING.equals(ei.getFilingStatus())) return ei;
        throw new BusinessException("仅待审核（PENDING）的备案可操作；当前 " + ei.getFilingStatus());
    }
}
