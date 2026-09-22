package com.tourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tourism.common.JwtUtil;
import com.tourism.common.Result;
import com.tourism.entity.User;
import com.tourism.exception.BizException;
import com.tourism.mapper.UserMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** 认证：前台游客注册/登录 + 管理员登录 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public AuthController(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    /** 密码统一 SHA-256 存储 */
    public static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RegisterDTO(String username, String password, String confirmPassword, String nickname) {}
    public record LoginDTO(String username, String password) {}

    @PostMapping("/auth/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.username()) || !StringUtils.hasText(dto.password()))
            throw new BizException("用户名和密码不能为空");
        if (dto.username().length() < 3 || dto.username().length() > 20)
            throw new BizException("用户名长度需为 3-20 个字符");
        if (dto.password().length() < 6)
            throw new BizException("密码长度不能少于 6 位");
        if (!dto.password().equals(dto.confirmPassword()))
            throw new BizException("两次输入的密码不一致");
        Long cnt = userMapper.selectCount(new QueryWrapper<User>().eq("username", dto.username()));
        if (cnt > 0) throw new BizException("用户名已被注册，请重新输入");

        User u = new User();
        u.setUsername(dto.username());
        u.setPassword(sha256(dto.password()));
        u.setNickname(StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.username());
        u.setRole("USER");
        u.setStatus(1);
        userMapper.insert(u);

        Map<String, Object> data = new HashMap<>();
        data.put("id", u.getId());
        data.put("username", u.getUsername());
        data.put("nickname", u.getNickname());
        return Result.ok(data);
    }

    /** 前台游客登录：仅允许 USER 角色、且未禁用 */
    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        User u = checkLogin(dto);
        if (!"USER".equals(u.getRole())) throw new BizException(403, "请使用管理员后台登录");
        if (u.getStatus() != null && u.getStatus() == 0) throw new BizException(403, "该账号已被禁用，请联系管理员");
        return Result.ok(buildLoginData(u));
    }

    /** 管理员登录：仅允许 ADMIN 角色 */
    @PostMapping("/admin/auth/login")
    public Result<Map<String, Object>> adminLogin(@RequestBody LoginDTO dto) {
        User u = checkLogin(dto);
        if (!"ADMIN".equals(u.getRole())) throw new BizException(403, "该账号不是管理员");
        return Result.ok(buildLoginData(u));
    }

    /** 校验用户名是否已被注册（注册页 blur 事件用） */
    @GetMapping("/auth/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        Long cnt = userMapper.selectCount(new QueryWrapper<User>().eq("username", username));
        return Result.ok(cnt == 0);
    }

    private User checkLogin(LoginDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.username()) || !StringUtils.hasText(dto.password()))
            throw new BizException("用户名和密码不能为空");
        User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.username()));
        if (u == null || !u.getPassword().equals(sha256(dto.password())))
            throw new BizException("用户名或密码错误");
        return u;
    }

    private Map<String, Object> buildLoginData(User u) {
        String token = jwtUtil.createToken(u.getId(), u.getUsername(), u.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", u.getId());
        data.put("username", u.getUsername());
        data.put("nickname", u.getNickname());
        data.put("role", u.getRole());
        return data;
    }
}
