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
@Table("publish_schedule")
public class PublishSchedule {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String articleTaskId;
    private LocalDateTime publishAt;
    private String status;
    /** 目标平台：wechat / xiaohongshu / douyin */
    private String platform;
    /** 转换后的平台内容（JSON：title,body,topics,issues,metadata） */
    private String adapterOutput;
    /** 转换后的标题 */
    private String contentTitle;
    /** 方法论模板 */
    private String methodologyName;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private LocalDateTime createTime;
}
