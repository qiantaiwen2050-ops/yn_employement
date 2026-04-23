package com.yn.employment.modules.business.notice;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("notice")
public class Notice {
    private Long id;
    private String title;
    private String content;
    private Long publisherId;
    private String publisherUsername;
    private String publisherRealName;
    private String publisherType;        // city | province
    private String publisherRegionCode;
    private String publisherRegionName;
    private String validUntil;
    private String createdAt;
    private String updatedAt;
    @TableLogic
    private Integer deleted;
}
