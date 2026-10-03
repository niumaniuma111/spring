package com.tourism.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tourism.common.Result;
import com.tourism.entity.Attraction;
import com.tourism.entity.Order;
import com.tourism.entity.User;
import com.tourism.mapper.AttractionMapper;
import com.tourism.mapper.OrderMapper;
import com.tourism.mapper.UserMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端：门票订单管理（全站订单分页/筛选/状态统计，只读——订单状态流转由用户端与超时关单驱动）。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final AttractionMapper attractionMapper;

    public AdminOrderController(OrderMapper o, UserMapper u, AttractionMapper a) {
        this.orderMapper = o;
        this.userMapper = u;
        this.attractionMapper = a;
    }

    /** 订单分页：keyword 匹配订单号/用户名，status 筛选（空=全部） */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        // 先按用户名预筛（订单表无用户名字段，逻辑外键不 join）
        List<Long> matched = null;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        if (kw != null) {
            List<Long> combined = new ArrayList<>();
            userMapper.selectList(new QueryWrapper<User>().like("username", kw))
                    .forEach(u -> combined.add(u.getId()));
            userMapper.selectList(new QueryWrapper<User>().like("nickname", kw))
                    .forEach(u -> combined.add(u.getId()));
            matched = combined.stream().distinct().toList();
            // 订单号命中的订单不限制用户
            boolean orderNoHit = orderMapper.selectCount(
                    new QueryWrapper<Order>().like("order_no", kw)) > 0;
            if (matched.isEmpty() && !orderNoHit) {
                return emptyPage(page, size);
            }
        }
        final List<Long> userIdFilter = matched;
        final String keywordFilter = kw;

        QueryWrapper<Order> qw = new QueryWrapper<>();
        // 只展示有效订单（待支付/已支付），已取消/已关闭不在后台展示
        qw.in("status", Order.STATUS_UNPAID, Order.STATUS_PAID);
        if (userIdFilter != null) {
            qw.and(w -> w.in("user_id", userIdFilter)
                    .or().like("order_no", keywordFilter));
        }
        if (status != null) qw.eq("status", status);
        qw.orderByDesc("id");

        Page<Order> p = orderMapper.selectPage(new Page<>(page, size), qw);
        List<Order> records = p.getRecords();

        // 批量联查用户与景点（避免 N+1）
        Map<Long, User> users = batchUsers(records);
        Map<Long, Attraction> spots = batchAttractions(records);

        List<Map<String, Object>> voList = records.stream()
                .map(o -> toVO(o, users.get(o.getUserId()),
                        spots.get(o.getAttractionId()))).toList();

        Map<String, Object> data = new HashMap<>();
        data.put("list", voList);
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("size", p.getSize());
        data.put("pages", p.getPages());
        return Result.ok(data);
    }

    /** 各状态订单数（筛选栏徽标用，只统计有效订单） */
    @GetMapping("/stats")
    public Result<Map<String, Object>> statusStats() {
        Map<String, Object> out = new HashMap<>();
        out.put("total", orderMapper.selectCount(new QueryWrapper<Order>()
                .in("status", Order.STATUS_UNPAID, Order.STATUS_PAID)));
        out.put("s0", orderMapper.selectCount(new QueryWrapper<Order>().eq("status", Order.STATUS_UNPAID)));
        out.put("s1", orderMapper.selectCount(new QueryWrapper<Order>().eq("status", Order.STATUS_PAID)));
        return Result.ok(out);
    }

    // ---------- 内部工具 ----------

    private Map<Long, User> batchUsers(List<Order> records) {
        if (records.isEmpty()) return Map.of();
        List<Long> ids = records.stream().map(Order::getUserId).distinct().toList();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Attraction> batchAttractions(List<Order> records) {
        if (records.isEmpty()) return Map.of();
        List<Long> ids = records.stream().map(Order::getAttractionId).distinct().toList();
        return attractionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Attraction::getId, Function.identity()));
    }

    private Map<String, Object> toVO(Order o, User u, Attraction a) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", o.getId());
        vo.put("orderNo", o.getOrderNo());
        vo.put("username", u != null ? u.getUsername() : "");
        vo.put("nickname", u != null ? u.getNickname() : "");
        vo.put("attractionName", a != null ? a.getName() : "（景点已删除）");
        vo.put("image", a != null ? a.getImage() : "");
        vo.put("quantity", o.getQuantity());
        vo.put("unitPrice", o.getUnitPrice());
        vo.put("totalAmount", o.getTotalAmount());
        vo.put("visitDate", o.getVisitDate() != null ? o.getVisitDate().toString() : null);
        vo.put("status", o.getStatus());
        vo.put("statusText", statusText(o.getStatus()));
        vo.put("paidAt", o.getPaidAt());
        vo.put("createdAt", o.getCreatedAt());
        return vo;
    }

    private String statusText(int s) {
        return switch (s) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已取消";
            case 3 -> "已关闭";
            default -> "未知";
        };
    }

    private Result<Map<String, Object>> emptyPage(long page, long size) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", new ArrayList<>());
        data.put("total", 0L);
        data.put("page", page);
        data.put("size", size);
        data.put("pages", 0);
        return Result.ok(data);
    }
}
