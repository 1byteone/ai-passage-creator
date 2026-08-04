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
@Table("workspace_member")
public class WorkspaceMember {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long workspaceId;
    private Long userId;
    private String role;
    private LocalDateTime joinedAt;

    /** 冗余展示字段：成员真实用户名（listMembers 时 join 填充，不落库） */
    private String userName;
    /** 冗余展示字段：成员头像（listMembers 时 join 填充，不落库） */
    private String userAvatar;
}
