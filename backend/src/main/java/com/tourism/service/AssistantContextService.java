package com.tourism.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tourism.entity.Attraction;
import com.tourism.entity.ChatMessage;
import com.tourism.entity.City;
import com.tourism.entity.Province;
import com.tourism.mapper.AttractionMapper;
import com.tourism.mapper.ChatMessageMapper;
import com.tourism.mapper.CityMapper;
import com.tourism.mapper.ProvinceMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 助手上下文组装：意图分流（行程规划 / 景点问答）+ 轻量 RAG
 * （检索数据库景点 → 组装紧凑清单注入 Prompt）+ 历史窗口裁剪
 * + 行程规划模式注入同城市景点对真实驾车距离（高德测距）。
 */
@Service
public class AssistantContextService {

    /** 历史消息窗口：只带最近 10 条，控制 token 成本 */
    private static final int HISTORY_WINDOW = 10;
    /** 注入 Prompt 的景点资料上限 */
    private static final int CONTEXT_LIMIT = 20;
    /** 距离注入：每城市最多取前 N 个高分景点计算两两距离 */
    private static final int DISTANCE_TOP_N = 5;

    private static final String SYSTEM_PROMPT = """
            你是「知行山水」旅游网站的 AI 行程助手。规则：
            1. 只推荐下方【景点资料】中列出的景点，禁止编造景点名称与门票价格；票价与开放时间必须与资料一致，资料未给出的信息回答"以景区公示为准"。
            2. 若用户想去的目的地不在资料中，可给通用建议，并注明"该地区暂未收录，建议在网站留言补充资料后再来查询"。
            3. 用简体中文，回复用 Markdown（行程单用标题+列表，可加表格），语气友好专业。
            4. 行程规划需考虑：景点地理位置就近串联、节奏适中（每天 2-3 个景点）、预算合理分配；
               若提供了【景点间驾车距离】，必须依据真实距离安排每日路线顺序，距离远的拆到不同天。
            5. 你可以调用工具查询实时天气、景点间驾车距离、搜索景点、查询任意地点（不限收录景点，含街道/商圈/地标）周边的酒店与餐饮；涉及"天气/远近/有哪些景点/附近有什么吃的住的"的问题，必须调用工具获取真实数据，禁止凭记忆编造。
            6. search_nearby_poi 的 keyword 支持任意地名（景点名、街道、商圈、城市名均可），无论该地点是否收录于网站资料，都能查到真实的周边 POI，请放心调用。
            """;

    private final AttractionMapper attractionMapper;
    private final ProvinceMapper provinceMapper;
    private final CityMapper cityMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AmapService amapService;

    public AssistantContextService(AttractionMapper a, ProvinceMapper p, CityMapper c,
                                   ChatMessageMapper m, AmapService s) {
        this.attractionMapper = a;
        this.provinceMapper = p;
        this.cityMapper = c;
        this.chatMessageMapper = m;
        this.amapService = s;
    }

    /** 意图分流：命中关键词 → 行程规划模式（temperature 更高、Prompt 更侧重行程组织） */
    public boolean isPlanning(String question) {
        return question.matches(".*(行程|路线|几天|攻略|规划|日程).*");
    }

    /**
     * 轻量 RAG 检索：问题中含省名/市名则查该地景点，否则全库按 rating 取 Top N。
     */
    public List<Attraction> searchAttractions(String question) {
        QueryWrapper<Attraction> qw = new QueryWrapper<>();
        qw.eq("status", 1);

        boolean hit = false;
        for (Province p : provinceMapper.selectList(null)) {
            if (question.contains(p.getName())) {
                qw.eq("province_id", p.getId());
                hit = true;
                break;
            }
        }
        if (!hit) {
            for (City c : cityMapper.selectList(null)) {
                if (question.contains(c.getName())) {
                    qw.eq("city_id", c.getId());
                    hit = true;
                    break;
                }
            }
        }
        qw.orderByDesc("rating", "views").last("limit " + CONTEXT_LIMIT);
        return attractionMapper.selectList(qw);
    }

    /** 组装本次对话消息：system + 最近历史窗口 + 本次user消息（景点资料 + 真实距离注入） */
    public List<Map<String, Object>> buildMessages(Long sessionId, String question,
                                                   boolean planning, Long excludeMessageId) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        // 最近 N 条历史（倒序取再反转；排除本次刚落库的 user 消息避免重复）
        List<ChatMessage> history = chatMessageMapper.selectList(new QueryWrapper<ChatMessage>()
                .eq("session_id", sessionId)
                .ne(excludeMessageId != null, "id", excludeMessageId)
                .orderByDesc("id")
                .last("limit " + HISTORY_WINDOW));
        Collections.reverse(history);
        for (ChatMessage m : history) {
            msgs.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        StringBuilder userContent = new StringBuilder();
        userContent.append("【景点资料】(名称|城市|等级|票价元|简介摘要)\n");
        List<Attraction> hits = searchAttractions(question);
        if (hits.isEmpty()) {
            userContent.append("（无匹配资料）\n");
        } else {
            for (Attraction a : hits) {
                userContent.append(formatAttraction(a)).append('\n');
            }
        }
        if (planning) {
            String distBlock = buildDistanceBlock(hits);
            if (!distBlock.isEmpty()) {
                userContent.append("\n【景点间驾车距离】(真实数据，安排行程顺序务必依据此块)\n").append(distBlock);
            }
        }
        userContent.append("\n【用户请求】").append(question);
        if (planning) {
            userContent.append("\n\n请根据以上资料为用户生成一份完整的多日行程单（含每日景点安排、顺序建议、预算估算与出行贴士）。");
        } else {
            userContent.append("\n\n请基于以上资料回答用户问题；资料不足以回答的部分明确说明。");
        }
        msgs.add(Map.of("role", "user", "content", userContent.toString()));
        return msgs;
    }

    /**
     * 真实距离块：同城市高分景点两两驾车距离（高德测距，带缓存）。
     * 跨城市不计算（行程按城市分区，跨城用文字提示）。
     */
    private String buildDistanceBlock(List<Attraction> hits) {
        if (!amapService.isAvailable()) return "";
        // 按城市分组
        Map<Long, List<Attraction>> byCity = new LinkedHashMap<>();
        for (Attraction a : hits) {
            if (a.getLng() == null) continue;
            byCity.computeIfAbsent(a.getCityId(), k -> new ArrayList<>()).add(a);
        }
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (List<Attraction> group : byCity.values()) {
            if (group.size() < 2) continue;
            List<Attraction> top = group.subList(0, Math.min(DISTANCE_TOP_N, group.size()));
            for (int i = 0; i < top.size(); i++) {
                for (int j = i + 1; j < top.size(); j++) {
                    Attraction a = top.get(i), b = top.get(j);
                    Long meters = amapService.drivingDistanceMeters(
                            a.getLng().doubleValue(), a.getLat().doubleValue(),
                            b.getLng().doubleValue(), b.getLat().doubleValue());
                    if (meters == null) continue;
                    long km = Math.round(meters / 1000.0);
                    long minutes = Math.round(meters / 1000.0 / 30.0 * 60);
                    String line = a.getName() + " ↔ " + b.getName() + ": 约" + km + "公里/驾车" + minutes + "分钟";
                    if (seen.add(line)) sb.append(line).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** 紧凑清单行：名称|城市|等级|票价|简介摘要(≤60字) */
    private String formatAttraction(Attraction a) {
        String intro = a.getDescription() == null ? "" : a.getDescription();
        if (intro.length() > 60) intro = intro.substring(0, 60) + "…";
        String price = a.getTicketPrice() == null ? "未知" : a.getTicketPrice().stripTrailingZeros().toPlainString();
        String cityName = "";
        City city = a.getCityId() == null ? null : cityMapper.selectById(a.getCityId());
        if (city != null) cityName = city.getName();
        return a.getName() + "|" + cityName + "|" + a.getLevel() + "|" + price + "元|" + intro;
    }
}
