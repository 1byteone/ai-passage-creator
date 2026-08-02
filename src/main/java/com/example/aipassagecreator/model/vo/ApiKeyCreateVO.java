package com.example.aipassagecreator.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建 API Key 响应 — 明文仅此一次返回，请立即保存
 */
@Data
public class ApiKeyCreateVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    /** 明文 token，仅此一次返回，落库后不可找回 */
    private String apiKey;

    private String apiKeyPrefix;
    private LocalDateTime expiresAt;
}
