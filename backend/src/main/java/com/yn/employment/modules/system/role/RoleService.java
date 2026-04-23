package com.yn.employment.modules.system.role;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermMapper;
    private final UserRoleMapper userRoleMapper;

    public RoleService(RoleMapper roleMapper, RolePermissionMapper rolePermMapper, UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.rolePermMapper = rolePermMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public List<RoleVO> listAll() {
        List<Role> roles = roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId));
        if (roles.isEmpty()) return List.of();
        Map<Long, List<String>> permMap = rolePermMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,
                        Collectors.mapping(RolePermission::getPermCode, Collectors.toList())));
        Map<Long, Long> userCountMap = countUsersByRole();
        return roles.stream().map(r -> new RoleVO(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getIsBuiltin() != null && r.getIsBuiltin() == 1,
                permMap.getOrDefault(r.getId(), List.of()),
                userCountMap.getOrDefault(r.getId(), 0L))).toList();
    }

    public RoleVO get(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException("角色不存在");
        List<String> perms = rolePermMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, id))
                .stream().map(RolePermission::getPermCode).toList();
        long uc = countUsersByRole().getOrDefault(id, 0L);
        return new RoleVO(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getIsBuiltin() != null && r.getIsBuiltin() == 1, perms, uc);
    }

    @Transactional
    public Role create(RoleDTO dto) {
        validate(dto);
        if (roleMapper.exists(Wrappers.<Role>lambdaQuery().eq(Role::getCode, dto.getCode())))
            throw new BusinessException("角色编码已存在");
        Role r = new Role();
        r.setCode(dto.getCode().trim());
        r.setName(dto.getName().trim());
        r.setDescription(dto.getDescription());
        r.setIsBuiltin(0);
        roleMapper.insert(r);
        savePerms(r.getId(), dto.getPermissions());
        return r;
    }

    @Transactional
    public Role update(Long id, RoleDTO dto) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException("角色不存在");
        if (dto.getName() != null && !dto.getName().isBlank()) r.setName(dto.getName().trim());
        if (dto.getDescription() != null) r.setDescription(dto.getDescription());
        // code is not editable
        roleMapper.updateById(r);
        if (dto.getPermissions() != null) {
            rolePermMapper.delete(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, id));
            savePerms(id, dto.getPermissions());
        }
        return r;
    }

    @Transactional
    public void delete(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException("角色不存在");
        if (r.getIsBuiltin() != null && r.getIsBuiltin() == 1)
            throw new BusinessException("内置角色不可删除");
        long uc = countUsersByRole().getOrDefault(id, 0L);
        if (uc > 0)
            throw new BusinessException("当前角色已分配给 " + uc + " 个用户，请先解除关联再删除");
        rolePermMapper.delete(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    public List<Long> roleIdsForUser(Long userId) {
        return userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
    }

    public List<String> permsForUser(Long userId) {
        List<Long> roleIds = roleIdsForUser(userId);
        if (roleIds.isEmpty()) return List.of();
        return rolePermMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .in(RolePermission::getRoleId, roleIds))
                .stream().map(RolePermission::getPermCode).distinct().toList();
    }

    @Transactional
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        if (roleIds == null) return;
        Set<Long> dedup = new HashSet<>(roleIds);
        for (Long rid : dedup) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(rid);
            userRoleMapper.insert(ur);
        }
    }

    public Role getByCode(String code) {
        return roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, code));
    }

    public Role insertBuiltin(String code, String name, String desc) {
        Role r = new Role();
        r.setCode(code); r.setName(name); r.setDescription(desc); r.setIsBuiltin(1);
        roleMapper.insert(r);
        return r;
    }

    public void savePerms(Long roleId, List<String> codes) {
        if (codes == null) return;
        for (String c : new HashSet<>(codes)) {
            if (c == null || c.isBlank()) continue;
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermCode(c.trim());
            rolePermMapper.insert(rp);
        }
    }

    private Map<Long, Long> countUsersByRole() {
        Map<Long, Long> map = new HashMap<>();
        for (UserRole ur : userRoleMapper.selectList(null)) {
            map.merge(ur.getRoleId(), 1L, Long::sum);
        }
        return map;
    }

    private void validate(RoleDTO dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) throw new BusinessException("角色编码不能为空");
        if (dto.getName() == null || dto.getName().isBlank()) throw new BusinessException("角色名称不能为空");
    }

    @Data
    @AllArgsConstructor
    public static class RoleVO {
        private Long id;
        private String code;
        private String name;
        private String description;
        private boolean builtin;
        private List<String> permissions;
        private long userCount;
    }

    @Data
    public static class RoleDTO {
        private String code;
        private String name;
        private String description;
        private List<String> permissions;
    }
}
