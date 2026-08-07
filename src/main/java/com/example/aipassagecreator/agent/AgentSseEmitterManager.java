package com.example.aipassagecreator.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Agent 对话流 SSE 管理器 — 环形缓冲 + 订阅回放 + 10min 超时（对齐 SkillSseEmitterManager） */
@Component
@Slf4j
public class AgentSseEmitterManager {

    private static final long TIMEOUT_MS = 10 * 60 * 1000L;
    private static final int MAX_BUFFER_SIZE = 200;
    private static final long TERMINAL_RETENTION_MINUTES = 10L;

    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-sse-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter subscribe(String requestId) {
        StreamState state = streams.computeIfAbsent(requestId, ignored -> new StreamState());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        synchronized (state) {
            completeQuietly(state.emitter);
            state.emitter = emitter;
            configureCallbacks(requestId, state, emitter);
            try {
                for (String event : state.events) {
                    send(emitter, event);
                }
                if (state.terminal) {
                    emitter.complete();
                }
            } catch (IOException e) {
                log.warn("Agent SSE 缓冲重放失败, requestId={}", requestId, e);
                emitter.completeWithError(e);
            }
        }
        return emitter;
    }

    public void publish(String requestId, String event) {
        StreamState state = streams.computeIfAbsent(requestId, ignored -> new StreamState());
        synchronized (state) {
            if (state.events.size() >= MAX_BUFFER_SIZE) {
                state.events.removeFirst();
            }
            state.events.addLast(event);
            if (state.emitter != null) {
                try {
                    send(state.emitter, event);
                } catch (IOException e) {
                    log.warn("Agent SSE 推送失败, requestId={}", requestId, e);
                    completeWithErrorQuietly(state.emitter, e);
                    state.emitter = null;
                }
            }
        }
    }

    public void complete(String requestId) {
        StreamState state = streams.computeIfAbsent(requestId, ignored -> new StreamState());
        synchronized (state) {
            state.terminal = true;
            completeQuietly(state.emitter);
            state.emitter = null;
        }
        cleanupExecutor.schedule(() -> streams.remove(requestId, state),
                TERMINAL_RETENTION_MINUTES, TimeUnit.MINUTES);
    }

    List<String> snapshot(String requestId) {
        StreamState state = streams.get(requestId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return new ArrayList<>(state.events);
        }
    }

    private void configureCallbacks(String requestId, StreamState state, SseEmitter emitter) {
        emitter.onTimeout(() -> clearEmitter(requestId, state, emitter));
        emitter.onCompletion(() -> clearEmitter(requestId, state, emitter));
        emitter.onError(error -> clearEmitter(requestId, state, emitter));
    }

    private void clearEmitter(String requestId, StreamState state, SseEmitter emitter) {
        synchronized (state) {
            if (state.emitter == emitter) {
                state.emitter = null;
            }
        }
        log.debug("Agent SSE 连接已释放, requestId={}", requestId);
    }

    private void send(SseEmitter emitter, String event) throws IOException {
        emitter.send(SseEmitter.event().data(event).reconnectTime(3000L));
    }

    private void completeQuietly(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE complete 失败: {}", e.getMessage());
        }
    }

    private void completeWithErrorQuietly(SseEmitter emitter, Throwable error) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.completeWithError(error);
        } catch (Exception e) {
            log.warn("SSE completeWithError 失败: {}", e.getMessage());
        }
    }

    private static class StreamState {
        private final Deque<String> events = new ArrayDeque<>();
        private SseEmitter emitter;
        private boolean terminal;
    }
}