package com.yn.employment.modules.system.role;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import com.yn.employment.modules.system.log.SysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/role")
public class RoleController {

    private final RoleService service;
    @Autowired private SysLogService sysLog;

    public RoleController(RoleService service) { this.service = service; }

    @GetMapping
    public Result<List<RoleService.RoleVO>> list() {
        requireProvince();
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public Result<RoleService.RoleVO> detail(@PathVariable Long id) {
        requireProvince();
        return Result.ok(service.get(id));
    }

    @PostMapping
    public Result<Role> create(@RequestBody RoleService.RoleDTO dto) {
        requireProvince();
        Role r = service.create(dto);
        sysLog.log("ROLE_CREATE", "role:" + r.getId(), "新增角色：" + r.getCode());
        return Result.ok(r);
    }

    @PutMapping("/{id}")
    public Result<Role> update(@PathVariable Long id, @RequestBody RoleService.RoleDTO dto) {
        requireProvince();
        Role r = service.update(id, dto);
        sysLog.log("ROLE_UPDATE", "role:" + id, "修改角色 + 权限：" + r.getCode());
        return Result.ok(r);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        requireProvince();
        service.delete(id);
        sysLog.log("ROLE_DELETE", "role:" + id, "删除角色");
        return Result.ok();
    }

    @GetMapping("/permissions")
    public Result<List<Permission.Item>> permissions() {
        requireProvince();
        return Result.ok(Permission.CATALOG);
    }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可操作");
    }
}
