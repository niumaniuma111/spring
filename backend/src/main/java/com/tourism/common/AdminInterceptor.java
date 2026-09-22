package com.tourism.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 管理端接口鉴权拦截器：校验 JWT 且要求 ADMIN 角色 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper om = new ObjectMapper();

    public AdminInterceptor(JwtUtil jwtUtil) { this.jwtUtil = jwtUtil; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String auth = req.getHeader("Authorization");
        Claims claims = (auth != null && auth.startsWith("Bearer ")) ? jwtUtil.parse(auth.substring(7)) : null;
        if (claims == null) {
            return reject(resp, 401, "未登录或登录已过期");
        }
        if (!"ADMIN".equals(claims.get("role", String.class))) {
            return reject(resp, 403, "无管理员权限");
        }
        req.setAttribute("uid", claims.get("uid", Long.class));
        req.setAttribute("username", claims.getSubject());
        return true;
    }

    private boolean reject(HttpServletResponse resp, int code, String msg) throws Exception {
        resp.setStatus(200);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(om.writeValueAsString(Result.fail(code, msg)));
        return false;
    }
}
