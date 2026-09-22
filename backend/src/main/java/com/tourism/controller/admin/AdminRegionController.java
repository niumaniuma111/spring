package com.tourism.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

/** 管理端：省市管理（列表/新增/修改/删除，删除前校验是否被景点引用） */
@RestController
@RequestMapping("/api/admin/regions")
public class AdminRegionController {

    private final ProvinceMapper provinceMapper;
    private final CityMapper cityMapper;
    private final AttractionMapper attractionMapper;

    public AdminRegionController(ProvinceMapper p, CityMapper c, AttractionMapper a) {
        this.provinceMapper = p; this.cityMapper = c; this.attractionMapper = a;
    }

    /** 省份及其下属城市树 */
    @GetMapping
    public Result<List<Map<String, Object>>> tree() {
        List<Province> provinces = provinceMapper.selectList(new QueryWrapper<Province>().orderByAsc("id"));
        List<City> cities = cityMapper.selectList(new QueryWrapper<City>().orderByAsc("id"));
        List<Map<String, Object>> list = provinces.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("cities", cities.stream().filter(c -> c.getProvinceId().equals(p.getId())).toList());
            return m;
        }).toList();
        return Result.ok(list);
    }

    // ---------- 省份 ----------
    @PostMapping("/provinces")
    public Result<Void> addProvince(@RequestBody Province p) {
        if (!StringUtils.hasText(p.getName())) throw new BizException("省份名称不能为空");
        if (provinceMapper.selectCount(new QueryWrapper<Province>().eq("name", p.getName().trim())) > 0)
            throw new BizException("省份已存在");
        p.setName(p.getName().trim());
        provinceMapper.insert(p);
        return Result.ok();
    }

    @PutMapping("/provinces/{id}")
    public Result<Void> updateProvince(@PathVariable Long id, @RequestBody Province p) {
        if (!StringUtils.hasText(p.getName())) throw new BizException("省份名称不能为空");
        if (provinceMapper.selectCount(new QueryWrapper<Province>().eq("name", p.getName().trim()).ne("id", id)) > 0)
            throw new BizException("省份名称已存在");
        Province db = provinceMapper.selectById(id);
        if (db == null) throw new BizException("省份不存在");
        db.setName(p.getName().trim());
        provinceMapper.updateById(db);
        return Result.ok();
    }

    @DeleteMapping("/provinces/{id}")
    public Result<Void> deleteProvince(@PathVariable Long id) {
        Long cnt = attractionMapper.selectCount(new QueryWrapper<Attraction>().eq("province_id", id));
        if (cnt > 0) throw new BizException("该省份下存在 " + cnt + " 条景点数据，不能删除，请先处理下属景点");
        Long cityCnt = cityMapper.selectCount(new QueryWrapper<City>().eq("province_id", id));
        if (cityCnt > 0) throw new BizException("该省份下存在 " + cityCnt + " 个城市，请先删除下属城市");
        provinceMapper.deleteById(id);
        return Result.ok();
    }

    // ---------- 城市 ----------
    @PostMapping("/cities")
    public Result<Void> addCity(@RequestBody City c) {
        if (!StringUtils.hasText(c.getName())) throw new BizException("城市名称不能为空");
        if (c.getProvinceId() == null || provinceMapper.selectById(c.getProvinceId()) == null)
            throw new BizException("所属省份不存在");
        if (cityMapper.selectCount(new QueryWrapper<City>().eq("province_id", c.getProvinceId()).eq("name", c.getName().trim())) > 0)
            throw new BizException("该省份下城市已存在");
        c.setName(c.getName().trim());
        cityMapper.insert(c);
        return Result.ok();
    }

    @PutMapping("/cities/{id}")
    public Result<Void> updateCity(@PathVariable Long id, @RequestBody City c) {
        if (!StringUtils.hasText(c.getName())) throw new BizException("城市名称不能为空");
        City db = cityMapper.selectById(id);
        if (db == null) throw new BizException("城市不存在");
        if (cityMapper.selectCount(new QueryWrapper<City>().eq("province_id", db.getProvinceId())
                .eq("name", c.getName().trim()).ne("id", id)) > 0)
            throw new BizException("该省份下城市名称已存在");
        db.setName(c.getName().trim());
        cityMapper.updateById(db);
        return Result.ok();
    }

    @DeleteMapping("/cities/{id}")
    public Result<Void> deleteCity(@PathVariable Long id) {
        Long cnt = attractionMapper.selectCount(new QueryWrapper<Attraction>().eq("city_id", id));
        if (cnt > 0) throw new BizException("该城市下存在 " + cnt + " 条景点数据，不能删除，请先处理下属景点");
        cityMapper.deleteById(id);
        return Result.ok();
    }
}
