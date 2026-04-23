package com.yn.employment.auth;

import com.yn.employment.common.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-hours}")
    private int expireHours;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UserContext.CurrentUser u) {
        long now = System.currentTimeMillis();
        long expire = now + expireHours * 3600_000L;
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("username", u.getUsername())
                .claim("realName", u.getRealName())
                .claim("userType", u.getUserType())
                .claim("regionCode", u.getRegionCode())
                .claim("regionName", u.getRegionName())
                .issuedAt(new Date(now))
                .expiration(new Date(expire))
                .signWith(key)
                .compact();
    }

    public UserContext.CurrentUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        UserContext.CurrentUser u = new UserContext.CurrentUser();
        u.setId(Long.valueOf(c.getSubject()));
        u.setUsername(c.get("username", String.class));
        u.setRealName(c.get("realName", String.class));
        u.setUserType(c.get("userType", String.class));
        u.setRegionCode(c.get("regionCode", String.class));
        u.setRegionName(c.get("regionName", String.class));
        return u;
    }
}
