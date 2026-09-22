package com.tourism.controller.admin;

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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理端：景点管理（列表/条件查询/新增/修改/删除） */
@RestController
@RequestMapping("/api/admin/attractions")
public class AdminAttractionController {

    private final AttractionMapper attractionMapper;
    private final ProvinceMapper provinceMapper;
    private final CityMapper cityMapper;

    public AdminAttractionController(AttractionMapper a, ProvinceMapper p, CityMapper c) {
        this.attractionMapper = a; this.provinceMapper = p; this.cityMapper = c;
    }

    /** 分页 + 条件查询（名称模糊/省市/等级），管理端可看到未发布景点 */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String level) {

        QueryWrapper<Attraction> qw = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) qw.like("name", keyword.trim());
        if (provinceId != null) qw.eq("province_id", provinceId);
        if (cityId != null) qw.eq("city_id", cityId);
        if (StringUtils.hasText(level)) qw.eq("level", level);
        qw.orderByDesc("id");

        Page<Attraction> p = attractionMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("total", p.getTotal());
        data.put("list", p.getRecords());
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<Attraction> detail(@PathVariable Long id) {
        return Result.ok(attractionMapper.selectById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody Attraction a) {
        validate(a);
        if (a.getRating() == null) a.setRating(new BigDecimal("4.0"));
        if (a.getViews() == null) a.setViews(0);
        if (a.getStatus() == null) a.setStatus(1);
        attractionMapper.insert(a);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Attraction a) {
        validate(a);
        a.setId(id);
        attractionMapper.updateById(a);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (attractionMapper.selectById(id) == null) throw new BizException("景点不存在");
        attractionMapper.deleteById(id);
        return Result.ok();
    }

    private void validate(Attraction a) {
        if (!StringUtils.hasText(a.getName())) throw new BizException("景点名称不能为空");
        if (a.getProvinceId() == null || a.getCityId() == null) throw new BizException("请选择所属省市");
        City city = cityMapper.selectById(a.getCityId());
        if (city == null || !city.getProvinceId().equals(a.getProvinceId()))
            throw new BizException("所属城市与省份不匹配");
        if (!StringUtils.hasText(a.getLevel())) throw new BizException("请选择景点等级");
        if (!List.of("5A", "4A", "3A", "2A", "1A").contains(a.getLevel()))
            throw new BizException("景点等级只能是 5A/4A/3A/2A/1A");
        if (a.getTicketPrice() != null && a.getTicketPrice().signum() < 0)
            throw new BizException("门票价格不能为负数");
        if (a.getRating() != null && (a.getRating().signum() < 0 || a.getRating().compareTo(new BigDecimal("5")) > 0))
            throw new BizException("综合评分需在 0-5 之间");
        // 防止越权设置省份数据来源：校验省份存在
        if (provinceMapper.selectById(a.getProvinceId()) == null) throw new BizException("省份不存在");
    }
}
