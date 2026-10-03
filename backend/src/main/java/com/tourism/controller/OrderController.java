package com.tourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tourism.common.Result;
import com.tourism.entity.Attraction;
import com.tourism.entity.Order;
import com.tourism.exception.BizException;
import com.tourism.mapper.AttractionMapper;
import com.tourism.mapper.OrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 门票订单：下单（票价快照）→ 模拟支付 / 取消 → 超时自动关闭（OrderTimeoutScheduler）。
 * 状态机：0待支付 → 1已支付 | 2已取消 | 3超时关闭。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** 每单限购票数 */
    private static final int MAX_QUANTITY = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderMapper orderMapper;
    private final AttractionMapper attractionMapper;

    public OrderController(OrderMapper o, AttractionMapper a) {
        this.orderMapper = o;
        this.attractionMapper = a;
    }

    public record CreateOrderDTO(Long attractionId, Integer quantity, String visitDate) {}

    /** 下单：校验景点上架/数量/日期，写入下单时票价快照 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateOrderDTO dto, HttpServletRequest req) {
        Long uid = uid(req);
        if (dto == null || dto.attractionId() == null)
            throw new BizException("参数不完整");
        int quantity = dto.quantity() == null ? 1 : dto.quantity();
        if (quantity < 1 || quantity > MAX_QUANTITY)
            throw new BizException("每单限购 " + MAX_QUANTITY + " 张");
        if (!StringUtils.hasText(dto.visitDate()))
            throw new BizException("请选择游玩日期");
        LocalDate visitDate;
        try {
            visitDate = LocalDate.parse(dto.visitDate());
        } catch (Exception e) {
            throw new BizException("游玩日期格式不正确");
        }
        if (visitDate.isBefore(LocalDate.now()))
            throw new BizException("游玩日期不能早于今天");

        Attraction a = attractionMapper.selectById(dto.attractionId());
        if (a == null || a.getStatus() == null || a.getStatus() != 1)
            throw new BizException("景点不存在或已下架");
        BigDecimal unitPrice = a.getTicketPrice() == null ? BigDecimal.ZERO : a.getTicketPrice();

        Order o = new Order();
        o.setOrderNo(genOrderNo());
        o.setUserId(uid);
        o.setAttractionId(a.getId());
        o.setQuantity(quantity);
        o.setUnitPrice(unitPrice);
        o.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        o.setVisitDate(visitDate);
        o.setStatus(Order.STATUS_UNPAID);
        orderMapper.insert(o);
        return Result.ok(toVO(o, a));
    }

    /** 我的订单（分页，附景点信息） */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "5") long size,
                                            HttpServletRequest req) {
        Long uid = uid(req);
        Page<Order> p = orderMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Order>().eq("user_id", uid).orderByDesc("id"));
        List<Map<String, Object>> records = p.getRecords().stream()
                .map(o -> toVO(o, attractionMapper.selectById(o.getAttractionId())))
                .toList();
        Map<String, Object> data = new HashMap<>();
        data.put("list", records);
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("size", p.getSize());
        data.put("pages", p.getPages());
        return Result.ok(data);
    }

    /** 订单详情 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest req) {
        Order o = owned(uid(req), id);
        return Result.ok(toVO(o, attractionMapper.selectById(o.getAttractionId())));
    }

    /** 模拟支付：0待支付 → 1已支付（幂等：非待支付状态报错） */
    @PostMapping("/{id}/pay")
    public Result<Map<String, Object>> pay(@PathVariable Long id, HttpServletRequest req) {
        Order o = owned(uid(req), id);
        if (o.getStatus() != Order.STATUS_UNPAID)
            throw new BizException("订单当前状态不可支付（" + statusText(o.getStatus()) + "）");
        o.setStatus(Order.STATUS_PAID);
        o.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(o);
        return Result.ok(toVO(o, attractionMapper.selectById(o.getAttractionId())));
    }

    /** 取消订单：0待支付 → 2已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id, HttpServletRequest req) {
        Order o = owned(uid(req), id);
        if (o.getStatus() != Order.STATUS_UNPAID)
            throw new BizException("仅待支付订单可取消（当前：" + statusText(o.getStatus()) + "）");
        o.setStatus(Order.STATUS_CANCELED);
        orderMapper.updateById(o);
        return Result.ok(toVO(o, attractionMapper.selectById(o.getAttractionId())));
    }

    // ---------- 内部工具 ----------

    private Long uid(HttpServletRequest req) {
        Long uid = (Long) req.getAttribute("uid");
        if (uid == null) throw new BizException(401, "请先登录");
        return uid;
    }

    /** 订单归属校验 */
    private Order owned(Long uid, Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) throw new BizException("订单不存在");
        if (!o.getUserId().equals(uid)) throw new BizException(403, "无权操作该订单");
        return o;
    }

    /** 订单号：T + yyyyMMddHHmmss + 6位随机数 */
    private String genOrderNo() {
        return "T" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String statusText(int s) {
        return switch (s) {
            case Order.STATUS_UNPAID -> "待支付";
            case Order.STATUS_PAID -> "已支付";
            case Order.STATUS_CANCELED -> "已取消";
            case Order.STATUS_CLOSED -> "已关闭";
            default -> "未知";
        };
    }

    /** 订单 VO：附带景点名称/图片/省市（下单时景点可能已改名，取当前值展示） */
    private Map<String, Object> toVO(Order o, Attraction a) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", o.getId());
        vo.put("orderNo", o.getOrderNo());
        vo.put("attractionId", o.getAttractionId());
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
}
