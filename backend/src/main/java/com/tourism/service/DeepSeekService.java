package com.tourism.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.config.DeepSeekProperties;
import com.tourism.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * DeepSeek 调用（OpenAI 兼容）：流式对话 + 工具调用(function calling)第一轮探测。
 * JDK 17 HttpClient 直调，零新增 Maven 依赖。
 */
@Service
public class DeepSeekService {

    private static final Pattern DATA_PREFIX = Pattern.compile("^data:\\s*");

    private final DeepSeekProperties props;
    private final ObjectMapper om;
    private final HttpClient client;

    public DeepSeekService(DeepSeekProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds()))
                .build();
    }

    /**
     * 流式对话：delta 通过回调吐出（打字机），返回聚合全文。
     * @param messages OpenAI 格式消息（role/content/tool_calls/tool_call_id）
     */
    @SuppressWarnings("unchecked")
    public String chatStream(List<Map<String, Object>> messages, double temperature, Consumer<String> onDelta) {
        HttpResponse<Stream<String>> response = (HttpResponse<Stream<String>>) send(messages, temperature, null, true);
        if (response.statusCode() != 200) {
            throw new BizException("AI 服务异常（HTTP " + response.statusCode() + "）");
        }

        StringBuilder full = new StringBuilder();
        response.body().forEach(line -> {
            if (line == null || !line.startsWith("data:")) return;
            String payload = DATA_PREFIX.matcher(line).replaceFirst("");
            if ("[DONE]".equals(payload)) return;
            try {
                String delta = om.readTree(payload)
                        .path("choices").path(0)
                        .path("delta").path("content").asText("");
                if (!delta.isEmpty()) {
                    full.append(delta);
                    onDelta.accept(delta);
                }
            } catch (Exception ignore) {
                // 单行解析失败不影响整体流
            }
        });
        return full.toString();
    }

    /**
     * 非流式单轮（工具调用探测用）：带 tools 定义，返回 choices[0] 节点。
     * 调用方检查 finish_reason：tool_calls 则执行工具后再走 chatStream 第二轮。
     */
    public JsonNode chatOnce(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                             double temperature) {
        HttpResponse<String> response = (HttpResponse<String>) send(messages, temperature, tools, false);
        if (response.statusCode() != 200) {
            throw new BizException("AI 服务异常（HTTP " + response.statusCode() + "）");
        }
        try {
            JsonNode root = om.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            if (choice.isMissingNode()) throw new BizException("AI 服务响应异常");
            return choice;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("AI 服务响应异常");
        }
    }

    /** 统一请求发送：tools 可空，stream 切换 */
    private Object send(List<Map<String, Object>> messages, double temperature,
                        List<Map<String, Object>> tools, boolean stream) {
        if (!StringUtils.hasText(props.getApiKey())) throw new BizException("AI 服务未配置");
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", 2048);
        if (tools != null && !tools.isEmpty()) body.put("tools", tools);
        if (stream) body.put("stream", true);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(props.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                    .build();
        } catch (Exception e) {
            throw new BizException("AI 请求构造失败");
        }
        try {
            if (stream) {
                return client.send(request, HttpResponse.BodyHandlers.ofLines());
            }
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new BizException("AI 服务连接失败，请稍后重试");
        }
    }
}
