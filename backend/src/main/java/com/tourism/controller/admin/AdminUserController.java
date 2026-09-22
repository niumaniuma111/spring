package com.tourism.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tourism.common.Result;
import com.tourism.entity.User;
import com.tourism.exception.BizException;
import com.tourism.mapper.UserMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 管理端：用户管理（分页/按用户名查询/禁用启用） */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserMapper userMapper;

    public AdminUserController(UserMapper userMapper) { this.userMapper = userMapper; }

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {

        QueryWrapper<User> qw = new QueryWrapper<>();
        // 只管理前台注册用户，管理员账号不在此列表展示
        qw.eq("role", "USER");
        if (StringUtils.hasText(keyword)) qw.like("username", keyword.trim());
        qw.orderByDesc("id");

        Page<User> p = userMapper.selectPage(new Page<>(page, size), qw);
        // 不回传密码
        p.getRecords().forEach(u -> u.setPassword(null));
        Map<String, Object> data = new HashMap<>();
        data.put("total", p.getTotal());
        data.put("list", p.getRecords());
        return Result.ok(data);
    }

    /** 禁用 / 启用 */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) throw new BizException("状态参数不合法");
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        if (!"USER".equals(u.getRole())) throw new BizException("不能操作管理员账号");
        u.setStatus(status);
        userMapper.updateById(u);
        return Result.ok();
    }
}
