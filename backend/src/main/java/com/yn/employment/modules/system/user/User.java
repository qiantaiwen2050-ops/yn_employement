package com.yn.employment.modules.system.user;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user")
public class User {
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String userType;     // province | city | enterprise
    private String regionCode;
    private String regionName;
    private Integer status;
    private String createdAt;
    private String updatedAt;
    @TableLogic
    private Integer deleted;
}
