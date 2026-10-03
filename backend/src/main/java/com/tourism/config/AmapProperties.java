package com.tourism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 高德开放平台配置（key 走环境变量注入，禁止写死） */
@Component
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {
    /** Web 服务 key（后端调天气/测距/地理编码） */
    private String key;
    /** Web端 JS key（前端地图，经 /api/amap/config 下发） */
    private String jsKey;
    /** JS API 安全密钥 */
    private String securityCode;
    private int timeoutSeconds = 10;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getJsKey() { return jsKey; }
    public void setJsKey(String jsKey) { this.jsKey = jsKey; }
    public String getSecurityCode() { return securityCode; }
    public void setSecurityCode(String securityCode) { this.securityCode = securityCode; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
