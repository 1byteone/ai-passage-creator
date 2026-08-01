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
@Table("article_quality")
public class ArticleQuality {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String taskId;
    private String articleContentSnapshot;
    private Integer structureScore;
    private Integer logicScore;
    private Integer languageScore;
    private Integer seoScore;
    private Integer readabilityScore;
    private Integer overallScore;
    private String suggestions;
    private String strengths;
    private String modelUsed;
    private Integer tokenUsage;
    private Integer durationMs;
    private LocalDateTime createTime;
}
