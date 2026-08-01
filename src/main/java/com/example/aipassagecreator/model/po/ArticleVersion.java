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
@Table("article_version")
public class ArticleVersion {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String taskId;
    private Integer versionNo;
    private Integer round;
    private String content;
    private String changeSummary;
    private String promptUsed;
    private Integer qualityScore;
    private Integer diffBaseVersion;
    private String modelUsed;
    private Integer tokenUsage;
    private Integer durationMs;
    private Long createdBy;
    private LocalDateTime createTime;
}
