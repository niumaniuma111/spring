package com.tourism.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tourism.entity.Order;
import com.tourism.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动关闭：每分钟扫描一次，
 * 创建超过 15 分钟仍待支付的订单置为"超时关闭"（模拟电商关单，防占票）。
 */
@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);
    /** 未支付订单保留时长（分钟） */
    private static final long TIMEOUT_MINUTES = 15;

    private final OrderMapper orderMapper;

    public OrderTimeoutScheduler(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void closeTimeoutOrders() {
        List<Order> timeouts = orderMapper.selectList(new QueryWrapper<Order>()
                .eq("status", Order.STATUS_UNPAID)
                .lt("created_at", LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES)));
        for (Order o : timeouts) {
            o.setStatus(Order.STATUS_CLOSED);
            orderMapper.updateById(o);
            log.info("订单超时关闭: orderNo={}", o.getOrderNo());
        }
        if (!timeouts.isEmpty()) {
            log.info("本次共关闭超时订单 {} 笔", timeouts.size());
        }
    }
}
