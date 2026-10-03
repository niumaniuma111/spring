package com.tourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tourism.common.Result;
import com.tourism.config.AmapProperties;
import com.tourism.entity.Attraction;
import com.tourism.entity.City;
import com.tourism.exception.BizException;
import com.tourism.mapper.AttractionMapper;
import com.tourism.mapper.CityMapper;
import com.tourism.service.AmapService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 高德地图：地图打点点位、详情天气、前端 JS key 下发。
 * key 未配置/失效时友好降级（前端显示提示，不崩）。
 */
@RestController
@RequestMapping("/api")
public class AmapController {

    private final AmapService amapService;
    private final AmapProperties amapProps;
    private final AttractionMapper attractionMapper;
    private final CityMapper cityMapper;

    public AmapController(AmapService s, AmapProperties p, AttractionMapper a, CityMapper c) {
        this.amapService = s;
        this.amapProps = p;
        this.attractionMapper = a;
        this.cityMapper = c;
    }

    /** 前端 JS key 下发（JS key 本就暴露于浏览器，安全密钥绑域名白名单） */
    @GetMapping("/amap/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> vo = new HashMap<>();
        vo.put("jsKey", amapProps.getJsKey());
        vo.put("securityCode", amapProps.getSecurityCode());
        vo.put("webKeyReady", amapService.isAvailable());
        return Result.ok(vo);
    }

    /** 管理端：坐标 → 地址（地图点选景点位置时自动生成详细地址），/api/admin/** 已受管理员拦截保护 */
    @GetMapping("/admin/amap/regeo")
    public Result<Map<String, Object>> adminRegeo(@RequestParam double lng, @RequestParam double lat) {
        Map<String, Object> g = amapService.regeo(lng, lat);
        if (g == null) throw new BizException("该位置无法解析出地址（可能在水域或境外），请换个位置点选");
        return Result.ok(g);
    }

    /** 管理端：地址 → 坐标（新增/编辑景点时表单内地图定位用），/api/admin/** 已受管理员拦截保护 */
    @GetMapping("/admin/amap/geocode")
    public Result<Map<String, Object>> adminGeocode(@RequestParam String address,
                                                    @RequestParam(required = false) String city) {
        Map<String, Object> g = amapService.geocode(address, city);
        if (g == null) throw new BizException("未匹配到该地址的坐标，请核对地址后重试");
        return Result.ok(g);
    }

    /** 全站景点地图打点点位（含坐标的已上架景点） */
    @GetMapping("/attractions/map")
    public Result<List<Map<String, Object>>> mapPoints() {
        List<Attraction> list = attractionMapper.selectList(new QueryWrapper<Attraction>()
                .eq("status", 1).isNotNull("lng").isNotNull("lat"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Attraction a : list) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", a.getId());
            vo.put("name", a.getName());
            vo.put("level", a.getLevel());
            vo.put("lng", a.getLng());
            vo.put("lat", a.getLat());
            vo.put("rating", a.getRating());
            vo.put("ticketPrice", a.getTicketPrice());
            City ct = a.getCityId() == null ? null : cityMapper.selectById(a.getCityId());
            vo.put("cityName", ct != null ? ct.getName() : "");
            out.add(vo);
        }
        return Result.ok(out);
    }

    /** 景点当地天气（实况 + 未来4天预报） */
    @GetMapping("/amap/weather")
    public Result<Map<String, Object>> weather(@RequestParam Long attractionId) {
        Attraction a = attractionMapper.selectById(attractionId);
        if (a == null) throw new BizException("景点不存在");
        City ct = a.getCityId() == null ? null : cityMapper.selectById(a.getCityId());
        if (ct == null || !StringUtils.hasText(ct.getAdcode()))
            throw new BizException("该城市暂无天气数据");
        return Result.ok(amapService.weather(ct.getAdcode()));
    }

    /** 周边真实 POI（酒店/餐饮），按景点坐标 3km 内、按距离排序 */
    @GetMapping("/amap/nearby")
    public Result<List<Map<String, Object>>> nearby(@RequestParam Long attractionId,
                                                    @RequestParam(defaultValue = "hotel") String type) {
        Attraction a = attractionMapper.selectById(attractionId);
        if (a == null) throw new BizException("景点不存在");
        if (a.getLng() == null) throw new BizException("该景点暂无坐标数据");
        return Result.ok(amapService.nearbyPOI(
                a.getLng().doubleValue(), a.getLat().doubleValue(), type));
    }

}
