package com.example.aipassagecreator.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** agent 请求 → 归属者（"u:{userId}" / "g:{guestId}"）登记，供 SSE 订阅时校验越权 */
@Component
public class AgentRequestRegistry {

    private final Map<String, String> owners = new ConcurrentHashMap<>();

    public void register(String requestId, String ownerKey) {
        owners.put(requestId, ownerKey);
    }

    public boolean isOwner(String requestId, String ownerKey) {
        return ownerKey != null && ownerKey.equals(owners.get(requestId));
    }

    public void remove(String requestId) {
        owners.remove(requestId);
    }
}