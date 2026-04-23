package com.yn.employment.modules.business.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import com.yn.employment.common.UserContext;
import com.yn.employment.modules.system.log.SysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NoticeService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NoticeMapper mapper;
    @Autowired private SysLogService sysLog;

    public NoticeService(NoticeMapper mapper) { this.mapper = mapper; }

    /** Notices the current user is allowed to *browse* (read-only). */
    public List<Notice> listVisible(String keyword) {
        UserContext.CurrentUser u = UserContext.require();
        LambdaQueryWrapper<Notice> q = Wrappers.<Notice>lambdaQuery()
                .like(notBlank(keyword), Notice::getTitle, keyword)
                .orderByDesc(Notice::getCreatedAt);
        switch (u.getUserType()) {
            case "enterprise" ->
                // see province notices + city notices in own region
                q.and(w -> w.eq(Notice::getPublisherType, "province")
                        .or(ww -> ww.eq(Notice::getPublisherType, "city")
                                .eq(Notice::getPublisherRegionCode, u.getRegionCode())));
            case "city" ->
                // see province notices + own region's city notices (which are usually own)
                q.and(w -> w.eq(Notice::getPublisherType, "province")
                        .or(ww -> ww.eq(Notice::getPublisherType, "city")
                                .eq(Notice::getPublisherRegionCode, u.getRegionCode())));
            // province: see everything
        }
        List<Notice> all = mapper.selectList(q);
        // Filter out expired
        String today = LocalDate.now().toString();
        return all.stream().filter(n -> n.getValidUntil() == null || n.getValidUntil().compareTo(today) >= 0).toList();
    }

    /** Notices the current user has authored (for publisher views). */
    public List<Notice> listMine() {
        UserContext.CurrentUser u = UserContext.require();
        return mapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getPublisherId, u.getId())
                .orderByDesc(Notice::getCreatedAt));
    }

    public Notice getOrThrow(Long id) {
        Notice n = mapper.selectById(id);
        if (n == null) throw new BusinessException("通知不存在");
        return n;
    }

    public Notice create(NoticeDTO dto) {
        UserContext.CurrentUser u = UserContext.require();
        requirePublisher(u);
        validate(dto);
        Notice n = new Notice();
        n.setTitle(dto.getTitle().trim());
        n.setContent(dto.getContent());
        n.setValidUntil(notBlank(dto.getValidUntil()) ? dto.getValidUntil() : null);
        n.setPublisherId(u.getId());
        n.setPublisherUsername(u.getUsername());
        n.setPublisherRealName(u.getRealName());
        n.setPublisherType(u.getUserType());
        n.setPublisherRegionCode(u.getRegionCode());
        n.setPublisherRegionName(u.getRegionName());
        String now = LocalDateTime.now().format(FMT);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        mapper.insert(n);
        sysLog.log("NOTICE_CREATE", "notice:" + n.getId(), n.getPublisherType() + " 发布通知：" + n.getTitle());
        return n;
    }

    public Notice update(Long id, NoticeDTO dto) {
        UserContext.CurrentUser u = UserContext.require();
        Notice n = getOrThrow(id);
        if (!n.getPublisherId().equals(u.getId()))
            throw new BusinessException(403, "只能修改本人发布的通知");
        validate(dto);
        n.setTitle(dto.getTitle().trim());
        n.setContent(dto.getContent());
        n.setValidUntil(notBlank(dto.getValidUntil()) ? dto.getValidUntil() : null);
        n.setUpdatedAt(LocalDateTime.now().format(FMT));
        mapper.updateById(n);
        sysLog.log("NOTICE_UPDATE", "notice:" + id, "修改通知：" + n.getTitle());
        return n;
    }

    public void delete(Long id) {
        UserContext.CurrentUser u = UserContext.require();
        Notice n = getOrThrow(id);
        if (!n.getPublisherId().equals(u.getId()))
            throw new BusinessException(403, "只能删除本人发布的通知");
        mapper.deleteById(id);
        sysLog.log("NOTICE_DELETE", "notice:" + id, "删除通知：" + n.getTitle());
    }

    private void requirePublisher(UserContext.CurrentUser u) {
        if (!"city".equals(u.getUserType()) && !"province".equals(u.getUserType()))
            throw new BusinessException(403, "仅市级或省级用户可发布通知");
    }

    private void validate(NoticeDTO d) {
        if (d.getTitle() == null || d.getTitle().isBlank()) throw new BusinessException("通知标题不能为空");
        if (d.getTitle().length() > 50) throw new BusinessException("通知标题不能超过 50 字");
        if (d.getContent() == null || d.getContent().isBlank()) throw new BusinessException("通知内容不能为空");
        if (d.getContent().length() > 2000) throw new BusinessException("通知内容不能超过 2000 字");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
