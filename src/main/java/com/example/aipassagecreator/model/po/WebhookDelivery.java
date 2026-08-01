package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("webhook_delivery")
public class WebhookDelivery {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String eventType;
    private String payload;
    private String targetUrl;
    private String signature;
    private Integer attemptCount;
    private String status;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
