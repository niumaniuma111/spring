package com.tourism.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.entity.Attraction;
import com.tourism.entity.City;
import com.tourism.mapper.AttractionMapper;
import com.tourism.mapper.CityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 工具调用（Function Calling）编排：
 * 第一轮非流式探测 DeepSeek 是否需要调用工具 → 本地执行（高德天气/测距、查库）→
 * 把工具结果以 role=tool 回填 → 第二轮流式生成最终回答。
 */
@Service
public class AssistantToolService {

    private static final Logger log = LoggerFactory.getLogger(AssistantToolService.class);

    private final DeepSeekService deepSeekService;
    private final AmapService amapService;
    private final AttractionMapper attractionMapper;
    private final CityMapper cityMapper;
    private final ObjectMapper om;

    /** 平均车速 30km/h（景区道路经验值），测距无耗时字段时估算 */
    private static final double AVG_SPEED_KMH = 30.0;

    public AssistantToolService(DeepSeekService d, AmapService a, AttractionMapper am,
                                CityMapper cm, ObjectMapper om) {
        this.deepSeekService = d;
        this.amapService = a;
        this.attractionMapper = am;
        this.cityMapper = cm;
        this.om = om;
    }

    // ---------- 工具定义（OpenAI function calling 格式） ----------

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("query_weather", "查询中国城市当前天气实况与未来4天预报，出行前建议调用",
                Map.of("city_name", Map.of("type", "string", "description", "城市名，如：成都"))));
        tools.add(fn("calc_driving_distance", "查询网站收录的两个景点之间的驾车距离与耗时",
                Map.of("from_spot", Map.of("type", "string", "description", "起点景点名"),
                       "to_spot", Map.of("type", "string", "description", "终点景点名"))));
        tools.add(fn("search_attractions", "按城市名或关键词搜索本网站收录的真实景点",
                Map.of("keyword", Map.of("type", "string", "description", "城市名或景点关键词"))));
        tools.add(fn("search_nearby_poi", "查询某景点或城市地点周边 3km 内真实存在的酒店住宿/美食餐饮（按距离排序）",
                Map.of("keyword", Map.of("type", "string", "description", "景点名或城市/地名，如：故宫、厦门"),
                       "poi_type", Map.of("type", "string", "description", "hotel=酒店住宿，food=美食餐饮"))));
        return tools;
    }

    private Map<String, Object> fn(String name, String desc, Map<String, Object> props) {
        return Map.of("type", "function", "function", Map.of(
                "name", name, "description", desc,
                "parameters", Map.of("type", "object", "properties", props,
                        "required", new ArrayList<>(props.keySet()))));
    }

    // ---------- 编排：探测 → 执行 → 二轮流式 ----------

    /**
     * 带工具能力的对话：返回聚合全文（工具轮非流式、最终回答流式打字机）。
     * @param baseMessages 已含 system/历史/本次用户消息（含景点资料注入）的消息列表
     */
    public String chatWithTools(List<Map<String, Object>> baseMessages, double temperature,
                                Consumer<String> onDelta) {
        // 第一轮：非流式探测是否需要工具
        JsonNode choice = deepSeekService.chatOnce(baseMessages, tools(), temperature);
        String finish = choice.path("finish_reason").asText("");
        JsonNode msg = choice.path("message");
        JsonNode toolCalls = msg.path("tool_calls");

        if (!"tool_calls".equals(finish) || !toolCalls.isArray() || toolCalls.isEmpty()) {
            // 无需工具：探测轮是非流式，这里分段回调还原打字机体验
            String content = msg.path("content").asText("");
            int chunk = 3;
            for (int i = 0; i < content.length(); i += chunk) {
                onDelta.accept(content.substring(i, Math.min(content.length(), i + chunk)));
                try {
                    Thread.sleep(12);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return content;
        }

        // 回填 assistant 消息（含 tool_calls 原文）
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", msg.path("content").asText(null));
        assistantMsg.put("tool_calls", om.valueToTree(toolCalls));
        baseMessages.add(assistantMsg);

        // 执行每个工具调用
        for (JsonNode tc : toolCalls) {
            String callId = tc.path("id").asText("");
            String name = tc.path("function").path("name").asText("");
            String args = tc.path("function").path("arguments").asText("{}");
            String result = executeTool(name, args);
            log.info("AI 工具调用: {}({}) -> {} 字符", name, args, result.length());
            Map<String, Object> toolMsg = new LinkedHashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", callId);
            toolMsg.put("content", result);
            baseMessages.add(toolMsg);
        }

        // 第二轮：流式生成最终回答（不再提供工具，强制文本输出）
        return deepSeekService.chatStream(baseMessages, temperature, onDelta);
    }

    // ---------- 工具执行 ----------

    private String executeTool(String name, String argsJson) {
        try {
            JsonNode args = om.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            return switch (name) {
                case "query_weather" -> toolWeather(args.path("city_name").asText(""));
                case "calc_driving_distance" -> toolDistance(
                        args.path("from_spot").asText(""), args.path("to_spot").asText(""));
                case "search_attractions" -> toolSearch(args.path("keyword").asText(""));
                case "search_nearby_poi" -> toolNearby(
                        args.path("keyword").asText(""), args.path("poi_type").asText("hotel"));
                default -> "未知工具：" + name;
            };
        } catch (Exception e) {
            return "工具执行失败：" + (e instanceof com.tourism.exception.BizException
                    ? e.getMessage() : "服务暂时不可用");
        }
    }

    private String toolWeather(String cityName) {
        if (cityName.isBlank()) return "缺少城市名";
        City ct = cityMapper.selectOne(new QueryWrapper<City>().likeRight("name", cityName).last("limit 1"));
        if (ct == null || ct.getAdcode() == null || ct.getAdcode().isBlank())
            return "城市「" + cityName + "」未收录或无天气编码";
        var w = amapService.weather(ct.getAdcode());
        JsonNode lives = om.valueToTree(w.get("lives")).path(0);
        StringBuilder sb = new StringBuilder();
        if (!lives.isMissingNode()) {
            sb.append("当前实况：").append(lives.path("weather").asText(""))
              .append(" ").append(lives.path("temperature").asText("")).append("℃")
              .append(" ").append(lives.path("winddirection").asText("")).append("风")
              .append(lives.path("windpower").asText("")).append("级；\n");
        }
        JsonNode casts = om.valueToTree(w.get("forecasts"));
        for (JsonNode c : casts) {
            sb.append(c.path("date").asText()).append(" ")
              .append(c.path("dayweather").asText()).append("转").append(c.path("nightweather").asText())
              .append(" ").append(c.path("nighttemp")).append("~").append(c.path("daytemp")).append("℃；\n");
        }
        return sb.toString().trim();
    }

    private String toolDistance(String fromSpot, String toSpot) {
        Attraction from = findSpot(fromSpot);
        Attraction to = findSpot(toSpot);
        if (from == null || to == null)
            return "未找到景点：" + (from == null ? fromSpot : toSpot) + "（请先 search_attractions 确认名称）";
        if (from.getLng() == null || to.getLng() == null)
            return "景点缺少坐标数据";
        Long meters = amapService.drivingDistanceMeters(
                from.getLng().doubleValue(), from.getLat().doubleValue(),
                to.getLng().doubleValue(), to.getLat().doubleValue());
        if (meters == null) return "距离查询失败（地图服务暂不可用）";
        long km = Math.round(meters / 1000.0);
        long minutes = Math.round(meters / 1000.0 / AVG_SPEED_KMH * 60);
        return String.format("从「%s」到「%s」驾车约 %d 公里，约 %d 分钟。", from.getName(), to.getName(), km, minutes);
    }

    private String toolSearch(String keyword) {
        if (keyword.isBlank()) return "缺少关键词";
        List<Attraction> hits = attractionMapper.selectList(new QueryWrapper<Attraction>()
                .eq("status", 1).and(w -> w.like("name", keyword).or().like("address", keyword))
                .orderByDesc("rating").last("limit 10"));
        if (hits.isEmpty()) return "未收录与「" + keyword + "」相关的景点";
        StringBuilder sb = new StringBuilder("共 " + hits.size() + " 个：\n");
        for (Attraction a : hits) {
            City ct = a.getCityId() == null ? null : cityMapper.selectById(a.getCityId());
            sb.append(a.getName()).append("|").append(ct == null ? "" : ct.getName())
              .append("|").append(a.getLevel()).append("|")
              .append(a.getTicketPrice()).append("元|").append(a.getAddress()).append("\n");
        }
        return sb.toString().trim();
    }

    /** 周边酒店/餐饮：keyword 先按景点查坐标，查不到再按地名地理编码 */
    private String toolNearby(String keyword, String poiType) {
        if (keyword.isBlank()) return "缺少地点关键词";
        String type = "food".equals(poiType) ? "food" : "hotel";
        double lng, lat;
        Attraction spot = findSpot(keyword);
        if (spot != null && spot.getLng() != null) {
            lng = spot.getLng().doubleValue();
            lat = spot.getLat().doubleValue();
        } else {
            // 非收录景点（如城市/地标名）：地理编码取坐标
            var g = amapService.geocode(keyword, "");
            if (g == null) return "未找到「" + keyword + "」的位置坐标";
            lng = (Double) g.get("lng");
            lat = (Double) g.get("lat");
        }
        var pois = amapService.nearbyPOI(lng, lat, type);
        if (pois.isEmpty()) return "「" + keyword + "」周边 3km 内暂无相关" + ("hotel".equals(type) ? "酒店" : "餐饮");
        StringBuilder sb = new StringBuilder("「" + keyword + "」周边 3km 内真实"
                + ("hotel".equals(type) ? "酒店住宿" : "美食餐饮") + "（按距离排序，共 " + pois.size() + " 家）：\n");
        for (var p : pois) {
            sb.append(p.get("name")).append("|距离约").append(p.get("distance")).append("米|")
              .append(p.get("address")).append("|电话:").append(p.get("tel")).append("\n");
        }
        return sb.toString().trim();
    }

    private Attraction findSpot(String name) {
        if (name == null || name.isBlank()) return null;
        List<Attraction> hits = attractionMapper.selectList(new QueryWrapper<Attraction>()
                .eq("status", 1).like("name", name.replace("景区", "").replace("风景区", ""))
                .last("limit 1"));
        return hits.isEmpty() ? null : hits.get(0);
    }
}
