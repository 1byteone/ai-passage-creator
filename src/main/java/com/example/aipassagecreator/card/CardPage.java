package com.example.aipassagecreator.card;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@Table("article_card")
public class CardPage {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String taskId;
    private Integer pageNo;
    private String pageType;
    private String style;
    private String imageUrl;
    private String imageKey;
    private Integer width;
    private Integer height;
    private Integer bytes;
    private String status;
    private String complianceReport;
    private String errorMessage;
    private Integer renderMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
