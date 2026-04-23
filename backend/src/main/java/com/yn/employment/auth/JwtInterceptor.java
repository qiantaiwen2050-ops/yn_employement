package com.yn.employment.auth;

import com.yn.employment.common.AuthException;
import com.yn.employment.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final JwtSessionTracker sessionTracker;

    @Value("${app.jwt.header}")
    private String header;

    @Value("${app.jwt.prefix}")
    private String prefix;

    public JwtInterceptor(JwtUtil jwtUtil, JwtSessionTracker sessionTracker) {
        this.jwtUtil = jwtUtil;
        this.sessionTracker = sessionTracker;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        String h = req.getHeader(header);
        if (h == null || !h.startsWith(prefix)) {
            throw new AuthException("缺少身份令牌");
        }
        String token = h.substring(prefix.length()).trim();
        try {
            UserContext.CurrentUser u = jwtUtil.parse(token);
            UserContext.set(u);
            sessionTracker.touch(u.getId());
            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            throw new AuthException("身份令牌无效或已过期");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();
    }
}
