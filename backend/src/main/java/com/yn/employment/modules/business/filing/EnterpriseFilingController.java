package com.yn.employment.modules.business.filing;

import com.yn.employment.common.BusinessException;
import com.yn.employment.common.Result;
import com.yn.employment.common.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/enterprise/filing")
public class EnterpriseFilingController {

    private final FilingService service;

    public EnterpriseFilingController(FilingService service) { this.service = service; }

    @GetMapping
    public Result<EnterpriseInfo> mine() {
        requireEnterprise();
        return Result.ok(service.getMine());
    }

    @PutMapping
    public Result<EnterpriseInfo> save(@RequestBody EnterpriseInfo body) {
        requireEnterprise();
        return Result.ok(service.save(body));
    }

    @PostMapping("/submit")
    public Result<EnterpriseInfo> submit(@RequestBody EnterpriseInfo body) {
        requireEnterprise();
        return Result.ok(service.submit(body));
    }

    private void requireEnterprise() {
        UserContext.CurrentUser u = UserContext.require();
        if (!"enterprise".equals(u.getUserType())) throw new BusinessException(403, "仅企业用户可操作");
    }
}
