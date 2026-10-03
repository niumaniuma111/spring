package com.tourism.config;

import com.tourism.common.AdminInterceptor;
import com.tourism.common.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;
    private final LoginInterceptor loginInterceptor;

    public WebConfig(AdminInterceptor adminInterceptor, LoginInterceptor loginInterceptor) {
        this.adminInterceptor = adminInterceptor;
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 管理端：需要管理员身份
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
        // 前台浏览：必须登录（注册/登录接口不拦截）
        // 注意 /api/img/**（上传图片读取）必须公开：<img> 标签无法携带 Bearer 头，
        // 图片内容本身是公开展示的景点封面，无鉴权必要
        // /api/orders/qrcode（模拟支付二维码）同样公开：<img> 无法携带 Bearer 头，内容为无敏感信息的演示文本
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/attractions", "/api/attractions/*", "/api/provinces", "/api/cities",
                        "/api/assistant/**", "/api/orders", "/api/orders/**",
                        "/api/attractions/map", "/api/amap/weather", "/api/amap/nearby")
                .excludePathPatterns("/api/orders/qrcode");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
