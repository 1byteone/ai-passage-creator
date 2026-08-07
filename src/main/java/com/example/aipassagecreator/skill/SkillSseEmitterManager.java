package com.example.aipassagecreator.skill;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@Slf4j
public class SkillSseEmitterManager {

    private static final long TIMEOUT_MS = 10 * 60 * 1000L;
    private static final int MAX_BUFFER_SIZE = 200;
    private static final long TERMINAL_RETENTION_MINUTES = 10L;

    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "skill-sse-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter subscribe(String executionId) {
        StreamState state = streams.computeIfAbsent(executionId, ignored -> new StreamState());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        synchronized (state) {
            completeQuietly(state.emitter);
            state.emitter = emitter;
            configureCallbacks(executionId, state, emitter);
            try {
                for (String event : state.events) {
                    send(emitter, event);
                }
                if (state.terminal) {
                    emitter.complete();
                }
            } catch (IOException e) {
                log.warn("Skill SSE 缓冲重放失败, executionId={}", executionId, e);
                emitter.completeWithError(e);
            }
        }
        return emitter;
    }

    public void publish(String executionId, String event) {
        StreamState state = streams.computeIfAbsent(executionId, ignored -> new StreamState());
        synchronized (state) {
            if (state.events.size() >= MAX_BUFFER_SIZE) {
                state.events.removeFirst();
            }
            state.events.addLast(event);

            if (state.emitter != null) {
                try {
                    send(state.emitter, event);
                } catch (IOException e) {
                    log.warn("Skill SSE 推送失败, executionId={}", executionId, e);
                    completeWithErrorQuietly(state.emitter, e);
                    state.emitter = null;
                }
            }

            // 同步派发给已注册监听器（skill→agent 桥接）；监听器异常不影响 SSI 主流程
            for (Consumer<String> listener : state.listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    log.warn("Skill SSE 监听器异常, executionId={}", executionId, e);
                }
            }
        }
    }

    /**
     * 注册事件监听（skill → agent 桥接用）。
     * 注册即回放当前缓冲，此后 publish 同步派发；streams 清理时随缓冲自动移除。
     */
    public void listen(String executionId, Consumer<String> listener) {
        StreamState state = streams.computeIfAbsent(executionId, ignored -> new StreamState());
        synchronized (state) {
            state.listeners.add(listener);
            for (String event : state.events) {
                listener.accept(event);
            }
        }
    }

    public void complete(String executionId) {
        StreamState state = streams.computeIfAbsent(executionId, ignored -> new StreamState());
        synchronized (state) {
            state.terminal = true;
            completeQuietly(state.emitter);
            state.emitter = null;
        }
        cleanupExecutor.schedule(() -> streams.remove(executionId, state),
                TERMINAL_RETENTION_MINUTES, TimeUnit.MINUTES);
    }

    List<String> snapshot(String executionId) {
        StreamState state = streams.get(executionId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return new ArrayList<>(state.events);
        }
    }

    private void configureCallbacks(String executionId, StreamState state, SseEmitter emitter) {
        emitter.onTimeout(() -> clearEmitter(executionId, state, emitter));
        emitter.onCompletion(() -> clearEmitter(executionId, state, emitter));
        emitter.onError(error -> clearEmitter(executionId, state, emitter));
    }

    private void clearEmitter(String executionId, StreamState state, SseEmitter emitter) {
        synchronized (state) {
            if (state.emitter == emitter) {
                state.emitter = null;
            }
        }
        log.debug("Skill SSE 连接已释放, executionId={}", executionId);
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
        private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
        private SseEmitter emitter;
        private boolean terminal;
    }
}
