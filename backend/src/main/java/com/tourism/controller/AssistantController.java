package com.tourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tourism.common.Result;
import com.tourism.entity.ChatMessage;
import com.tourism.entity.ChatSession;
import com.tourism.exception.BizException;
import com.tourism.mapper.ChatMessageMapper;
import com.tourism.mapper.ChatSessionMapper;
import com.tourism.service.AssistantContextService;
import com.tourism.service.AssistantToolService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 行程助手：会话管理（CRUD）+ 流式聊天（SSE）。
 * 流程：登录校验由 LoginInterceptor 完成（uid 注入 request attribute）→
 * 用户消息落库 → 独立线程池调 DeepSeek 流式生成 → SseEmitter 逐段推送 → 全文落库。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    /** 输入长度上限（控制 token 成本） */
    private static final int MAX_INPUT_LEN = 2000;
    /** SSE 超时：大模型长回复 + 网络抖动，给足 3 分钟 */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AssistantToolService toolService;
    private final AssistantContextService contextService;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    public AssistantController(ChatSessionMapper s, ChatMessageMapper m,
                               AssistantToolService t, AssistantContextService c) {
        this.sessionMapper = s;
        this.messageMapper = m;
        this.toolService = t;
        this.contextService = c;
    }

    /** 新建会话 */
    @PostMapping("/sessions")
    public Result<Map<String, Object>> create(@RequestBody(required = false) Map<String, String> body,
                                              HttpServletRequest req) {
        Long uid = uid(req);
        String title = (body != null && StringUtils.hasText(body.get("title")))
                ? body.get("title").trim() : "新对话";
        ChatSession s = new ChatSession(uid, truncate(title, 64));
        sessionMapper.insert(s);
        Map<String, Object> data = new HashMap<>();
        data.put("id", s.getId());
        data.put("title", s.getTitle());
        return Result.ok(data);
    }

    /** 我的会话列表（按最近更新倒序） */
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions(HttpServletRequest req) {
        Long uid = uid(req);
        List<ChatSession> list = sessionMapper.selectList(new QueryWrapper<ChatSession>()
                .eq("user_id", uid).orderByDesc("updated_at"));
        return Result.ok(list.stream().map(s -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", s.getId());
            vo.put("title", s.getTitle());
            vo.put("updatedAt", s.getUpdatedAt());
            return vo;
        }).toList());
    }

    /** 会话历史消息（正序） */
    @GetMapping("/sessions/{id}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable Long id, HttpServletRequest req) {
        checkOwner(uid(req), id);
        List<ChatMessage> list = messageMapper.selectList(new QueryWrapper<ChatMessage>()
                .eq("session_id", id).orderByAsc("id"));
        return Result.ok(list.stream().map(m -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", m.getId());
            vo.put("role", m.getRole());
            vo.put("content", m.getContent());
            vo.put("createdAt", m.getCreatedAt());
            return vo;
        }).toList());
    }

    /** 删除会话（连带消息，硬删） */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        checkOwner(uid(req), id);
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", id));
        sessionMapper.deleteById(id);
        return Result.ok(null);
    }

    /**
     * 发消息（SSE 流式）：POST + SseEmitter（EventSource 不支持 POST，前端用 fetch 流式读取）。
     * 事件协议：delta=增量文本 / done=结束 / error=统一错误出口。
     */
    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long uid = uid(req);
        if (body == null || body.get("sessionId") == null || body.get("message") == null)
            throw new BizException("参数不完整");
        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        String message = body.get("message").toString().trim();
        if (!StringUtils.hasText(message)) throw new BizException("消息不能为空");
        if (message.length() > MAX_INPUT_LEN) throw new BizException("消息过长（最多 " + MAX_INPUT_LEN + " 字）");
        checkOwner(uid, sessionId);

        // 用户消息先落库（即使 AI 调用失败，对话历史也完整）
        ChatMessage userMsg = new ChatMessage(sessionId, "user", message);
        messageMapper.insert(userMsg);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Long excludeId = userMsg.getId();
        pool.execute(() -> {
            try {
                boolean planning = contextService.isPlanning(message);
                List<Map<String, Object>> msgs =
                        contextService.buildMessages(sessionId, message, planning, excludeId);
                String full = toolService.chatWithTools(msgs, planning ? 0.7 : 0.5,
                        delta -> send(emitter, "delta", Map.of("content", delta)));

                ChatMessage saved = new ChatMessage(sessionId, "assistant", full);
                messageMapper.insert(saved);
                send(emitter, "done", Map.of("messageId", saved.getId()));

                // 首条消息时用其前 20 字作为会话标题；同时刷新会话 updated_at
                touchSession(sessionId, message);
            } catch (Exception e) {
                send(emitter, "error", Map.of("message", friendly(e)));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    // ---------- 内部工具 ----------

    private Long uid(HttpServletRequest req) {
        Long uid = (Long) req.getAttribute("uid");
        if (uid == null) throw new BizException(401, "请先登录");
        return uid;
    }

    /** 会话归属校验：只能操作自己的会话 */
    private void checkOwner(Long uid, Long sessionId) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) throw new BizException("会话不存在");
        if (!s.getUserId().equals(uid)) throw new BizException(403, "无权访问该会话");
    }

    private void touchSession(Long sessionId, String firstMessage) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) return;
        UpdateWrapper<ChatSession> uw = new UpdateWrapper<>();
        uw.eq("id", sessionId).set("updated_at", java.time.LocalDateTime.now());
        if ("新对话".equals(s.getTitle()) && StringUtils.hasText(firstMessage)) {
            uw.set("title", truncate(firstMessage.replaceAll("\\s+", " "), 20));
        }
        sessionMapper.update(null, uw);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ignore) {
            // 客户端断开等发送失败：静默忽略，finally 里会 complete
        }
    }

    /** 异常转友好文案（不向前端泄露堆栈） */
    private String friendly(Exception e) {
        if (e instanceof BizException biz) return biz.getMessage();
        return "AI 服务繁忙，请稍后重试";
    }
}
