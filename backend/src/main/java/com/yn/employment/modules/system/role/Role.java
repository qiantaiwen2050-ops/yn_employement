package com.yn.employment.modules.system.role;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role")
public class Role {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer isBuiltin;
    private String createdAt;
}
