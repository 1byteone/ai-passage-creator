package com.example.aipassagecreator.model.dto.apikey;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建 API Key
 */
@Data
public class ApiKeyCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 归属用户（仅 admin 可指定，缺省为当前登录用户） */
    private Long userId;

    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称最长 64 字符")
    private String name;

    /** 过期时间（可空，表示永不过期） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}
