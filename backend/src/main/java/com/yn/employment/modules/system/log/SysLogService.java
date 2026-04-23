package com.yn.employment.modules.system.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Audit log writer. Every "consequential" write operation should call
 * SysLogService.log(action, target, detail) so the operation log can answer
 * "who did what to which entity, when". Reads are not logged.
 */
@Slf4j
@Service
public class SysLogService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysLogMapper mapper;

    public SysLogService(SysLogMapper mapper) { this.mapper = mapper; }

    public void log(String action, String target, String detail) {
        try {
            UserContext.CurrentUser u = UserContext.get();
            SysLog l = new SysLog();
            l.setUserId(u != null ? u.getId() : null);
            l.setUsername(u != null ? u.getUsername() : "system");
            l.setAction(action);
            l.setTarget(target);
            l.setDetail(detail);
            l.setIp(currentIp());
            l.setCreatedAt(LocalDateTime.now().format(FMT));
            mapper.insert(l);
        } catch (Exception e) {
            // Audit log failures must NEVER break business operations.
            log.warn("Failed to write sys_log [{} / {}]: {}", action, target, e.getMessage());
        }
    }

    public List<SysLog> list(String action, String username, String startDate, String endDate, int limit) {
        LambdaQueryWrapper<SysLog> q = Wrappers.<SysLog>lambdaQuery()
                .like(action != null && !action.isBlank(), SysLog::getAction, action)
                .like(username != null && !username.isBlank(), SysLog::getUsername, username)
                .ge(startDate != null && !startDate.isBlank(), SysLog::getCreatedAt, startDate + " 00:00:00")
                .le(endDate != null && !endDate.isBlank(), SysLog::getCreatedAt, endDate + " 23:59:59")
                .orderByDesc(SysLog::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 1000));
        return mapper.selectList(q);
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String fwd = req.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
