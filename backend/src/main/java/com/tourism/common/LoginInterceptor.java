package com.tourism.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 前台游客接口登录拦截：未登录不能浏览景点内容 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper om = new ObjectMapper();

    public LoginInterceptor(JwtUtil jwtUtil) { this.jwtUtil = jwtUtil; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String auth = req.getHeader("Authorization");
        Claims claims = (auth != null && auth.startsWith("Bearer ")) ? jwtUtil.parse(auth.substring(7)) : null;
        if (claims == null) {
            resp.setStatus(200);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write(om.writeValueAsString(Result.fail(401, "请先登录后浏览")));
            return false;
        }
        req.setAttribute("uid", claims.get("uid", Long.class));
        req.setAttribute("username", claims.getSubject());
        return true;
    }
}
