package com.yn.employment.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser u) { HOLDER.set(u); }
    public static CurrentUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static CurrentUser require() {
        CurrentUser u = HOLDER.get();
        if (u == null) throw new AuthException("未登录");
        return u;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentUser {
        private Long id;
        private String username;
        private String realName;
        private String userType;       // province | city | enterprise
        private String regionCode;
        private String regionName;
    }
}
