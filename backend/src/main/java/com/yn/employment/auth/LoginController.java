package com.yn.employment.auth;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import com.yn.employment.config.PasswordConfig;
import com.yn.employment.modules.system.log.SysLogService;
import com.yn.employment.modules.system.user.User;
import com.yn.employment.modules.system.user.UserService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class LoginController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordConfig.PasswordEncoder pwd;
    private final SysLogService sysLog;

    public LoginController(UserService userService, JwtUtil jwtUtil, PasswordConfig.PasswordEncoder pwd, SysLogService sysLog) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.pwd = pwd;
        this.sysLog = sysLog;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @jakarta.validation.Valid LoginRequest req) {
        User u = userService.getByUsername(req.getUsername());
        if (u == null) throw new BusinessException("账号或密码错误");
        if (u.getStatus() == null || u.getStatus() != 1) throw new BusinessException("账号已停用");
        if (!pwd.matches(req.getPassword(), u.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        UserContext.CurrentUser cu = new UserContext.CurrentUser(
                u.getId(), u.getUsername(), u.getRealName(),
                u.getUserType(), u.getRegionCode(), u.getRegionName());
        String token = jwtUtil.issue(cu);
        // Audit
        UserContext.set(cu);   // make ctx available for SysLogService.currentIp()
        sysLog.log("LOGIN", "user:" + u.getUsername(), "登录成功");
        UserContext.clear();
        return Result.ok(new LoginResponse(token, cu));
    }

    @GetMapping("/me")
    public Result<UserContext.CurrentUser> me() {
        return Result.ok(UserContext.require());
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        // Stateless JWT — frontend simply discards the token.
        return Result.ok();
    }

    @PostMapping("/change-password")
    public Result<?> changePassword(@RequestBody @jakarta.validation.Valid ChangePasswordRequest req) {
        UserContext.CurrentUser cu = UserContext.require();
        User u = userService.getById(cu.getId());
        if (u == null) throw new BusinessException("用户不存在");
        if (!pwd.matches(req.getOldPassword(), u.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        if (req.getNewPassword().equals(req.getOldPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        if (req.getNewPassword().length() < 8 || req.getNewPassword().length() > 20) {
            throw new BusinessException("新密码长度需在 8~20 位之间");
        }
        // Complexity: at least two of {digit, letter, special}
        int classes = 0;
        if (req.getNewPassword().matches(".*\\d.*")) classes++;
        if (req.getNewPassword().matches(".*[A-Za-z].*")) classes++;
        if (req.getNewPassword().matches(".*[^A-Za-z0-9].*")) classes++;
        if (classes < 2) throw new BusinessException("新密码须包含数字、字母、特殊字符中的至少两类");
        u.setPassword(pwd.encode(req.getNewPassword()));
        userService.save(u);
        sysLog.log("CHANGE_PASSWORD", "user:" + u.getUsername(), "用户修改密码");
        return Result.ok();
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class LoginResponse {
        private final String token;
        private final UserContext.CurrentUser user;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        private String newPassword;
        @NotBlank(message = "确认密码不能为空")
        private String confirmPassword;
    }
}
