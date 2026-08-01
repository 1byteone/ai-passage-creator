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
@Table("workspace_resource")
public class WorkspaceResource {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long workspaceId;
    private String resourceType;
    private String resourceId;
    private Long createdBy;
    private LocalDateTime createTime;
}
