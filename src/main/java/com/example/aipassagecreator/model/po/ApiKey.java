package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "api_key", camelToUnderline = false)
public class ApiKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** id（雪花算法） */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 用途名称 */
    private String name;

    /** token 的 SHA-256 hex，明文不落库 */
    private String apiKeyHash;

    /** 脱敏前缀（展示用） */
    private String apiKeyPrefix;

    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;

    /** 过期时间，null 表示永不过期 */
    private LocalDateTime expiresAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
