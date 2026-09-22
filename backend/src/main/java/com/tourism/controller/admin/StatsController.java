package com.tourism.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tourism.common.Result;
import com.tourism.entity.Attraction;
import com.tourism.entity.Province;
import com.tourism.mapper.AttractionMapper;
import com.tourism.mapper.ProvinceMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理端：数据统计（景点总数 / 各等级分布 / 各省数量） */
@RestController
@RequestMapping("/api/admin/stats")
public class StatsController {

    private final AttractionMapper attractionMapper;
    private final ProvinceMapper provinceMapper;

    public StatsController(AttractionMapper a, ProvinceMapper p) {
        this.attractionMapper = a; this.provinceMapper = p;
    }

    @GetMapping
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();

        // 1. 景点总数（已发布）
        Long total = attractionMapper.selectCount(new QueryWrapper<Attraction>().eq("status", 1));
        data.put("total", total);

        // 2. 各等级景点分布（5A~1A 固定顺序）
        List<String> levels = List.of("5A", "4A", "3A", "2A", "1A");
        List<Map<String, Object>> byLevel = new ArrayList<>();
        for (String lv : levels) {
            Long c = attractionMapper.selectCount(new QueryWrapper<Attraction>().eq("status", 1).eq("level", lv));
            Map<String, Object> m = new HashMap<>();
            m.put("level", lv);
            m.put("count", c);
            byLevel.add(m);
        }
        data.put("byLevel", byLevel);

        // 3. 各省景点数量（含 0 的省份也列出，便于图表完整）
        List<Province> provinces = provinceMapper.selectList(new QueryWrapper<Province>().orderByAsc("id"));
        List<Map<String, Object>> byProvince = new ArrayList<>();
        for (Province p : provinces) {
            Long c = attractionMapper.selectCount(new QueryWrapper<Attraction>().eq("status", 1).eq("province_id", p.getId()));
            Map<String, Object> m = new HashMap<>();
            m.put("province", p.getName());
            m.put("count", c);
            byProvince.add(m);
        }
        data.put("byProvince", byProvince);

        return Result.ok(data);
    }
}
