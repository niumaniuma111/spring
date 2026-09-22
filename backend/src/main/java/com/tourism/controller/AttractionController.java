package com.tourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tourism.common.Result;
import com.tourism.entity.Attraction;
import com.tourism.entity.City;
import com.tourism.entity.Province;
import com.tourism.exception.BizException;
import com.tourism.mapper.AttractionMapper;
import com.tourism.mapper.CityMapper;
import com.tourism.mapper.ProvinceMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 前台游客端：景点浏览（列表/筛选/搜索/排序/详情/省市联动） */
@RestController
@RequestMapping("/api")
public class AttractionController {

    private final AttractionMapper attractionMapper;
    private final ProvinceMapper provinceMapper;
    private final CityMapper cityMapper;

    public AttractionController(AttractionMapper a, ProvinceMapper p, CityMapper c) {
        this.attractionMapper = a; this.provinceMapper = p; this.cityMapper = c;
    }

    /**
     * 分页查询已发布景点
     * @param keyword    景点名称模糊搜索
     * @param provinceId 省份
     * @param cityId     城市
     * @param level      等级 5A/4A/3A/2A/1A
     * @param sort       排序：rating 综合评分降序 | views 浏览热度降序；缺省按 id
     */
    @GetMapping("/attractions")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "6") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String sort) {

        QueryWrapper<Attraction> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        if (StringUtils.hasText(keyword)) qw.like("name", keyword.trim());
        if (provinceId != null) qw.eq("province_id", provinceId);
        if (cityId != null) qw.eq("city_id", cityId);
        if (StringUtils.hasText(level)) qw.eq("level", level);
        if ("rating".equals(sort)) qw.orderByDesc("rating", "views");
        else if ("views".equals(sort)) qw.orderByDesc("views", "rating");
        else qw.orderByAsc("id");

        Page<Attraction> p = attractionMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> records = p.getRecords().stream().map(this::toVO).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("list", records);
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("size", p.getSize());
        data.put("pages", p.getPages());
        return Result.ok(data);
    }

    /** 景点详情（浏览热度 +1） */
    @GetMapping("/attractions/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Attraction a = attractionMapper.selectById(id);
        if (a == null || a.getStatus() == null || a.getStatus() != 1) throw new BizException("景点不存在或已下架");
        attractionMapper.incrementViews(id);
        a.setViews(a.getViews() == null ? 1 : a.getViews() + 1);
        return Result.ok(toVO(a));
    }

    /** 省份列表（前台筛选 + 后台表单共用） */
    @GetMapping("/provinces")
    public Result<List<Province>> provinces() {
        return Result.ok(provinceMapper.selectList(new QueryWrapper<Province>().orderByAsc("id")));
    }

    /** 省份联动城市列表 */
    @GetMapping("/cities")
    public Result<List<City>> cities(@RequestParam Long provinceId) {
        return Result.ok(cityMapper.selectList(new QueryWrapper<City>().eq("province_id", provinceId).orderByAsc("id")));
    }

    /** 组装前台 VO：附带省市名称 */
    private Map<String, Object> toVO(Attraction a) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", a.getId());
        vo.put("name", a.getName());
        vo.put("level", a.getLevel());
        vo.put("image", a.getImage());
        vo.put("description", a.getDescription());
        vo.put("address", a.getAddress());
        vo.put("openTime", a.getOpenTime());
        vo.put("ticketPrice", a.getTicketPrice());
        vo.put("rating", a.getRating());
        vo.put("views", a.getViews());
        vo.put("provinceId", a.getProvinceId());
        vo.put("cityId", a.getCityId());
        Province province = provinceMapper.selectById(a.getProvinceId());
        vo.put("provinceName", province != null ? province.getName() : "");
        City city = cityMapper.selectById(a.getCityId());
        vo.put("cityName", city != null ? city.getName() : "");
        return vo;
    }
}
