package com.yn.employment.config;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordConfig {

    /** Hutool BCrypt wrapper exposed as a bean. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(String raw) {
                return BCrypt.hashpw(raw, BCrypt.gensalt());
            }
            @Override
            public boolean matches(String raw, String hashed) {
                if (hashed == null || hashed.isEmpty()) return false;
                try {
                    return BCrypt.checkpw(raw, hashed);
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }

    public interface PasswordEncoder {
        String encode(String raw);
        boolean matches(String raw, String hashed);
    }
}
