package com.yn.employment.modules.business.notice;

import com.yn.employment.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) { this.service = service; }

    @GetMapping
    public Result<List<Notice>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(service.listVisible(keyword));
    }

    @GetMapping("/mine")
    public Result<List<Notice>> mine() {
        return Result.ok(service.listMine());
    }

    @GetMapping("/{id}")
    public Result<Notice> detail(@PathVariable Long id) {
        return Result.ok(service.getOrThrow(id));
    }

    @PostMapping
    public Result<Notice> create(@RequestBody NoticeDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Notice> update(@PathVariable Long id, @RequestBody NoticeDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
