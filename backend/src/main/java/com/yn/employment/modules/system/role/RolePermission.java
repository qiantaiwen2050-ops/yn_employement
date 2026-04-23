package com.yn.employment.modules.system.role;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_permission")
public class RolePermission {
    private Long roleId;
    private String permCode;
}
