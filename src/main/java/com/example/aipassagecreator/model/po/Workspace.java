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
@Table("workspace")
public class Workspace {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private Integer memberCount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
