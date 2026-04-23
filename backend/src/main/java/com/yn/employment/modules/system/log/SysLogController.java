package com.yn.employment.modules.system.log;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/log")
public class SysLogController {

    private final SysLogService service;

    public SysLogController(SysLogService service) { this.service = service; }

    @GetMapping
    public Result<List<SysLog>> list(@RequestParam(required = false) String action,
                                     @RequestParam(required = false) String username,
                                     @RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate,
                                     @RequestParam(defaultValue = "200") int limit) {
        requireProvince();
        return Result.ok(service.list(action, username, startDate, endDate, limit));
    }

    private void requireProvince() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"province".equals(u.getUserType())) throw new BusinessException(403, "仅省级用户可查询日志");
    }
}
