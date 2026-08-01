package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    /** 评测类型：GENERIC / VIRAL */
    private String scoreType;
    /** 归属用户 ID */
    private Long userId;
    /** 爆款加权综合分 */
    private BigDecimal viralScore;
    /** 各爆款维度分（JSON） */
    private String viralScores;
    /** 标题策略命中 */
    private String titleStrategyHit;
    /** 评测所用方法论 */
    private String methodologyUsed;
    /** 内容快照 hash（幂等） */
    private String contentHash;
    /** 评测绑定版本号 */
    private Integer versionNo;
}
