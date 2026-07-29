package com.example.aipassagecreator.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class LoginUserVO implements Serializable {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String userAccount;
    private String userName;
    private String userAvatar;
    private String userProfile;
    private String userRole;
    /**
     * 剩余配额
     */
    private Integer quota;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime vipTime;

}
