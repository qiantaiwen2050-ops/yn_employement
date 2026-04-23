package com.yn.employment.modules.business.notice;

import lombok.Data;

@Data
public class NoticeDTO {
    private String title;
    private String content;
    private String validUntil;   // YYYY-MM-DD or null
}
