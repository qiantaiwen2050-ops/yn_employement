package com.yn.employment.modules.system.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import com.yn.employment.config.PasswordConfig;
import com.yn.employment.modules.business.filing.EnterpriseInfo;
import com.yn.employment.modules.business.filing.EnterpriseInfoMapper;
import com.yn.employment.modules.business.report.Report;
import com.yn.employment.modules.business.report.ReportMapper;
import com.yn.employment.modules.system.log.SysLogService;
import com.yn.employment.modules.system.role.RoleService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/system/user")
public class UserAdminController {

    private static final Set<String> VALID_TYPES = Set.of("province", "city", "enterprise");

    private final UserService userService;
    private final UserMapper userMapper;
    private final RoleService roleService;
    private final ReportMapper reportMapper;
    private final EnterpriseInfoMapper enterpriseInfoMapper;
    private final PasswordConfig.PasswordEncoder pwd;
    @Autowired private SysLogService sysLog;

    public UserAdminController(UserService userService, UserMapper userMapper, RoleService roleService,
                               ReportMapper reportMapper, EnterpriseInfoMapper enterpriseInfoMapper,
                               PasswordConfig.PasswordEncoder pwd) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.roleService = roleService;
        this.reportMapper = reportMapper;
        this.enterpriseInfoMapper = enterpriseInfoMapper;
        this.pwd = pwd;
    }

    @GetMapping
    public Result<List<UserVO>> list(@RequestParam(required = false) String userType,
                                     @RequestParam(required = false) String regionCode,
                                     @RequestParam(required = false) String keyword) {
        requireProvince();
        var q = Wrappers.<User>lambdaQuery()
                .eq(userType != null && !userType.isBlank(), User::getUserType, userType)
                .eq(regionCode != null && !regionCode.isBlank(), User::getRegionCode, regionCode)
                .orderByAsc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(User::getUsername, keyword).or().like(User::getRealName, keyword));
        }
        List<User> users = userMapper.selectList(q);
        return Result.ok(users.stream().map(this::enrich).toList());
    }

    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        requireProvince();
        User u = userService.getById(id);
        if (u == null) throw new BusinessException("用户不存在");
        return Result.ok(enrich(u));
    }

    @PostMapping
    @Transactional
    public Result<UserVO> create(@RequestBody UserDTO dto) {
        requireProvince();
        if (dto.getUsername() == null || dto.getUsername().isBlank()) throw new BusinessException("登录账号不能为空");
        if (userService.getByUsername(dto.getUsername().trim()) != null)
            throw new BusinessException("登录账号已存在");
        if (dto.getUserType() == null || !VALID_TYPES.contains(dto.getUserType()))
            throw new BusinessException("用户类型必须是 province / city / enterprise");
        if (!"province".equals(dto.getUserType()) && (dto.getRegionCode() == null || dto.getRegionCode().isBlank()))
            throw new BusinessException("市级 / 企业用户必须指定所属地区");
        if (dto.getRoleIds() == null || dto.getRoleIds().isEmpty())
            throw new BusinessException("必须为新用户分配至少一个角色");

        User u = new User();
        u.setUsername(dto.getUsername().trim());
        u.setPassword(pwd.encode(dto.getPassword() == null || dto.getPassword().isBlank() ? "123456" : dto.getPassword()));
        u.setRealName(dto.getRealName());
        u.setUserType(dto.getUserType());
        u.setRegionCode(dto.getRegionCode());
        u.setRegionName(dto.getRegionName());
        u.setStatus(1);
        userService.save(u);

        roleService.assignRolesToUser(u.getId(), dto.getRoleIds());
        sysLog.log("USER_CREATE", "user:" + u.getId(), "新增用户：" + u.getUsername() + " (" + u.getUserType() + ")");
        return Result.ok(enrich(userService.getById(u.getId())));
    }

    @PutMapping("/{id}")
    @Transactional
    public Result<UserVO> update(@PathVariable Long id, @RequestBody UserDTO dto) {
        requireProvince();
        User u = userService.getById(id);
        if (u == null) throw new BusinessException("用户不存在");
        // username & userType not editable
        if (dto.getRealName() != null) u.setRealName(dto.getRealName());
        if (dto.getRegionCode() != null) u.setRegionCode(dto.getRegionCode());
        if (dto.getRegionName() != null) u.setRegionName(dto.getRegionName());
        if (dto.getStatus() != null) u.setStatus(dto.getStatus());
        userService.save(u);
        if (dto.getRoleIds() != null) roleService.assignRolesToUser(id, dto.getRoleIds());
        sysLog.log("USER_UPDATE", "user:" + id, "修改用户：" + u.getUsername());
        return Result.ok(enrich(u));
    }

    @PostMapping("/{id}/reset-password")
    public Result<?> resetPassword(@PathVariable Long id) {
        requireProvince();
        User u = userService.getById(id);
        if (u == null) throw new BusinessException("用户不存在");
        u.setPassword(pwd.encode("123456"));
        userService.save(u);
        sysLog.log("USER_RESET_PWD", "user:" + id, "重置密码：" + u.getUsername());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Result<?> delete(@PathVariable Long id) {
        requireProvince();
        User u = userService.getById(id);
        if (u == null) throw new BusinessException("用户不存在");
        UserContext.CurrentUser cur = UserContext.require();
        if (cur.getId().equals(id)) throw new BusinessException("不能删除当前登录账户");
        // Block deletion if enterprise has any reports
        if ("enterprise".equals(u.getUserType())) {
            EnterpriseInfo ei = enterpriseInfoMapper.selectOne(
                    Wrappers.<EnterpriseInfo>lambdaQuery().eq(EnterpriseInfo::getUserId, id));
            if (ei != null) {
                Long count = reportMapper.selectCount(Wrappers.<Report>lambdaQuery().eq(Report::getEnterpriseId, ei.getId()));
                if (count != null && count > 0)
                    throw new BusinessException("该企业已上报 " + count + " 条数据，不允许删除");
            }
        }
        roleService.assignRolesToUser(id, List.of());  // clears mappings
        userMapper.deleteById(id);
        sysLog.log("USER_DELETE", "user:" + id, "删除用户：" + u.getUsername());
        return Result.ok();
    }

    private UserVO enrich(User u) {
        return new UserVO(u.getId(), u.getUsername(), u.getRealName(), u.getUserType(),
                u.getRegionCode(), u.getRegionName(), u.getStatus(), u.getCreatedAt(),
                roleService.roleIdsForUser(u.getId()));
    }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
    }

    @Data
    @AllArgsConstructor
    public static class UserVO {
        private Long id;
        private String username;
        private String realName;
        private String userType;
        private String regionCode;
        private String regionName;
        private Integer status;
        private String createdAt;
        private List<Long> roleIds;
    }

    @Data
    public static class UserDTO {
        private String username;
        private String password;
        private String realName;
        private String userType;
        private String regionCode;
        private String regionName;
        private Integer status;
        private List<Long> roleIds;
    }
}
