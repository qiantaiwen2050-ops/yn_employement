package com.yn.employment.modules.system.dict;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_dict_item")
public class DictItem {
    private Long id;
    private String dictType;
    private String itemCode;
    private String itemName;
    private String parentCode;
    private Integer sortOrder;
}
