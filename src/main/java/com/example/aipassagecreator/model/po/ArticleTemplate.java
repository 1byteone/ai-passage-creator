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
@Table("article_template")
public class ArticleTemplate {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String name;
    private String description;
    private String category;
    private String style;
    private String structureJson;
    private String defaultPrompt;
    private Boolean isPublic;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
