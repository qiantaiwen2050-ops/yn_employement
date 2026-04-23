package com.yn.employment.modules.system.dict;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yn.employment.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dict")
public class DictController {

    private final DictItemMapper mapper;

    public DictController(DictItemMapper mapper) { this.mapper = mapper; }

    @GetMapping("/{type}")
    public Result<List<DictItem>> list(@PathVariable String type) {
        return Result.ok(mapper.selectList(
                Wrappers.<DictItem>lambdaQuery()
                        .eq(DictItem::getDictType, type)
                        .orderByAsc(DictItem::getSortOrder)));
    }

    /** Bulk fetch — returns a map keyed by dict type. */
    @GetMapping
    public Result<Map<String, List<DictItem>>> bulk(@RequestParam List<String> types) {
        List<DictItem> items = mapper.selectList(
                Wrappers.<DictItem>lambdaQuery()
                        .in(DictItem::getDictType, types)
                        .orderByAsc(DictItem::getSortOrder));
        Map<String, List<DictItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(DictItem::getDictType));
        // Ensure all requested keys exist (empty list for missing)
        Map<String, List<DictItem>> result = new HashMap<>();
        for (String t : types) result.put(t, grouped.getOrDefault(t, List.of()));
        return Result.ok(result);
    }
}
