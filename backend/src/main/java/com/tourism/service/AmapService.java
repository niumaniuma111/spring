package com.tourism.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.config.AmapProperties;
import com.tourism.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高德 Web 服务封装：天气（实况+预报）、驾车测距（内存缓存）。
 * key 失效/网络异常统一抛 BizException（友好文案），前端展示降级提示。
 */
@Service
public class AmapService {

    private final AmapProperties props;
    private final ObjectMapper om;
    private final HttpClient client = HttpClient.newHttpClient();
    /** 测距缓存 key = "lng1,lat1|lng2,lat2" → 米（高德免费额度有限，能省则省） */
    private final Map<String, Long> distanceCache = new ConcurrentHashMap<>();

    public AmapService(AmapProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
    }

    /** Web 服务 key 是否可用（前端/接口据此降级） */
    public boolean isAvailable() {
        return StringUtils.hasText(props.getKey());
    }

    /**
     * 逆地理编码：坐标 → 地址描述（管理员在地图上点选景点位置时自动生成详细地址）。
     * @return {address, adcode}；无匹配或服务异常返回 null
     */
    public Map<String, Object> regeo(double lng, double lat) {
        if (!isAvailable()) return null;
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?output=JSON&key=" + props.getKey()
                    + "&location=" + round(lng) + "," + round(lat);
            JsonNode root = read(get(url));
            if (!"1".equals(root.path("status").asText())) return null;
            String addr = root.path("regeocode").path("formatted_address").asText("");
            if (!StringUtils.hasText(addr)) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("address", addr);
            out.put("adcode", root.path("regeocode").path("addressComponent").path("adcode").asText(""));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 地理编码：详细地址 → 坐标 + adcode（管理员新增/修改景点时自动打点用）。
     * @return {lng, lat, adcode}；无匹配或服务异常返回 null（调用方静默降级）
     */
    public Map<String, Object> geocode(String address, String city) {
        if (!isAvailable() || !StringUtils.hasText(address)) return null;
        try {
            String url = "https://restapi.amap.com/v3/geocode/geo?output=JSON&key=" + props.getKey()
                    + "&address=" + java.net.URLEncoder.encode(address, "UTF-8")
                    + (StringUtils.hasText(city)
                        ? "&city=" + java.net.URLEncoder.encode(city, "UTF-8") : "");
            JsonNode root = read(get(url));
            if (!"1".equals(root.path("status").asText())) return null;
            JsonNode g = root.path("geocodes").path(0);
            if (g.isMissingNode() || !StringUtils.hasText(g.path("location").asText())) return null;
            String[] parts = g.path("location").asText().split(",");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lng", Double.parseDouble(parts[0]));
            out.put("lat", Double.parseDouble(parts[1]));
            out.put("adcode", g.path("adcode").asText(""));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 周边 POI 搜索：景点坐标周边 3km 内的真实酒店/餐饮（高德 place/around）。
     * @param type hotel=住宿服务(10000) food=餐饮服务(05000)
     * @return POI 列表（name/address/distance/tel），带 10 分钟缓存
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> nearbyPOI(double lng, double lat, String type) {
        return nearbyPOI(lng, lat, type, 3000);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> nearbyPOI(double lng, double lat, String type, int radius) {
        String cacheKey = round(lng) + "," + round(lat) + "|" + type + "|" + radius;
        Long at = poiCacheAt.get(cacheKey);
        if (at != null && System.currentTimeMillis() - at < WEATHER_CACHE_MS) {
            List<Map<String, Object>> cached = poiCache.get(cacheKey);
            if (cached != null) return cached;
        }
        // v3 place/around 的 types 大类码（10000/05000）查不到数据，必须用 keywords 或细分码；
        // 实测 keywords=酒店(600条)/餐厅(83条) 均有效
        String keywords = "hotel".equals(type) ? "酒店" : "餐厅";
        int r = Math.max(500, Math.min(radius, 10000));
        String url = "https://restapi.amap.com/v3/place/around?output=JSON&key=" + props.getKey()
                + "&location=" + round(lng) + "," + round(lat)
                + "&keywords=" + java.net.URLEncoder.encode(keywords, java.nio.charset.StandardCharsets.UTF_8)
                + "&radius=" + r + "&offset=6&page=1&sortrule=distance";
        JsonNode root = read(get(url));
        if (!"1".equals(root.path("status").asText()))
            throw new BizException("周边搜索失败：" + root.path("info").asText(""));
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode poi : root.path("pois")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", poi.path("name").asText(""));
            m.put("address", poi.path("address").isMissingNode() ? "" : poi.path("address").asText(""));
            m.put("distance", poi.path("distance").asText(""));
            m.put("tel", poi.path("tel").isMissingNode() ? "" : poi.path("tel").asText(""));
            m.put("location", poi.path("location").asText(""));  // POI 坐标（地图图层打点用）
            out.add(m);
        }
        poiCache.put(cacheKey, out);
        poiCacheAt.put(cacheKey, System.currentTimeMillis());
        return out;
    }

    /** POI 缓存：坐标+类型 → 10 分钟 */
    private final Map<String, List<Map<String, Object>>> poiCache = new ConcurrentHashMap<>();
    private final Map<String, Long> poiCacheAt = new ConcurrentHashMap<>();

    /** 天气缓存：adcode → (时间戳ms, 数据)，10 分钟过期（省免费额度 + 加速重复访问） */
    private final Map<String, Map<String, Object>> weatherCache = new ConcurrentHashMap<>();
    private final Map<String, Long> weatherCacheAt = new ConcurrentHashMap<>();
    private static final long WEATHER_CACHE_MS = 600_000L;

    /**
     * 天气：实况（extensions=base）+ 预报（extensions=all）。
     * 高德这两个参数互斥，需分两次调用。
     */
    public Map<String, Object> weather(String adcode) {
        if (!isAvailable()) throw new BizException("地图服务未配置");
        Long at = weatherCacheAt.get(adcode);
        if (at != null && System.currentTimeMillis() - at < WEATHER_CACHE_MS) {
            Map<String, Object> cached = weatherCache.get(adcode);
            if (cached != null) return cached;
        }
        JsonNode base = read(get("https://restapi.amap.com/v3/weather/weatherInfo"
                + "?city=" + adcode + "&key=" + props.getKey() + "&extensions=base&output=JSON"));
        // 预报请求：免费 key QPS=3，连续调用易被瞬时限流，失败重试一次
        JsonNode all = read(get("https://restapi.amap.com/v3/weather/weatherInfo"
                + "?city=" + adcode + "&key=" + props.getKey() + "&extensions=all&output=JSON"));
        if (!"1".equals(all.path("status").asText())) {
            try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            all = read(get("https://restapi.amap.com/v3/weather/weatherInfo"
                    + "?city=" + adcode + "&key=" + props.getKey() + "&extensions=all&output=JSON"));
        }
        if (!"1".equals(base.path("status").asText()))
            throw new BizException("天气查询失败：" + base.path("info").asText(""));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lives", base.path("lives"));                                   // 实况
        out.put("forecasts", all.path("forecasts").path(0).path("casts"));      // 未来4天预报
        weatherCache.put(adcode, out);
        weatherCacheAt.put(adcode, System.currentTimeMillis());
        return out;
    }

    /** 驾车距离（米）：两点间，带缓存；失败返回 null（调用方降级） */
    public Long drivingDistanceMeters(double lng1, double lat1, double lng2, double lat2) {
        if (!isAvailable()) return null;
        String cacheKey = round(lng1) + "," + round(lat1) + "|" + round(lng2) + "," + round(lat2);
        // 两方向视为同一路线近似值，任一方向命中即用
        String cacheKeyRev = round(lng2) + "," + round(lat2) + "|" + round(lng1) + "," + round(lat1);
        Long cached = distanceCache.get(cacheKey);
        if (cached == null) cached = distanceCache.get(cacheKeyRev);
        if (cached != null) return cached;

        // 高德测距：origins 最多 100 个点，这里两点一次调用；type=1 驾车
        String url = "https://restapi.amap.com/v3/distance?type=1&output=JSON&key=" + props.getKey()
                + "&origins=" + round(lng1) + "," + round(lat1)
                + "&destination=" + round(lng2) + "," + round(lat2);
        try {
            JsonNode root = read(get(url));
            if ("1".equals(root.path("status").asText()) && root.path("results").size() > 0) {
                long meters = root.path("results").path(0).path("distance").asLong();
                distanceCache.put(cacheKey, meters);
                return meters;
            }
        } catch (Exception ignore) {
            // 高德异常时返回 null，调用方降级为估算
        }
        return null;
    }

    /** 批量：一个终点对多个起点（测距 API 单次最多 100 origins，省调用次数） */
    public Map<String, Long> drivingDistancesTo(List<double[]> origins, double[] dest) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (!isAvailable()) return out;
        List<double[]> pending = new ArrayList<>();
        for (double[] o : origins) {
            Long cached = drivingDistanceMeters(o[0], o[1], dest[0], dest[1]);
            if (cached != null) {
                out.put(keyOf(o), cached);
            } else {
                pending.add(o);
            }
        }
        if (pending.isEmpty()) return out;
        StringBuilder os = new StringBuilder();
        for (double[] o : pending) {
            if (os.length() > 0) os.append(';');
            os.append(round(o[0])).append(',').append(round(o[1]));
        }
        String url = "https://restapi.amap.com/v3/distance?type=1&output=JSON&key=" + props.getKey()
                + "&origins=" + os + "&destination=" + round(dest[0]) + "," + round(dest[1]);
        try {
            JsonNode root = read(get(url));
            if ("1".equals(root.path("status").asText())) {
                JsonNode results = root.path("results");
                for (int i = 0; i < results.size() && i < pending.size(); i++) {
                    long meters = results.path(i).path("distance").asLong();
                    double[] o = pending.get(i);
                    out.put(keyOf(o), meters);
                    distanceCache.put(keyOf(o) + "|" + round(dest[0]) + "," + round(dest[1]), meters);
                }
            }
        } catch (Exception ignore) { }
        return out;
    }

    private String keyOf(double[] o) { return round(o[0]) + "," + round(o[1]); }

    private String round(double v) {
        return String.format("%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))   // URL 参数由调用方自行编码（URLEncoder），此处不可二次编码
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new BizException("地图服务异常");
            return resp.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("地图服务连接失败");
        }
    }

    private JsonNode read(String body) {
        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new BizException("地图服务响应异常");
        }
    }
}
