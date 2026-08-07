package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RAG 参考溯源记录 — 创作各阶段注入的检索参考。
 * <p>文章生成时把检索命中（自己的历史文章 / 共享文档）持久化到本表，
 * 详情页可按 taskId 溯源「本次创作参考了哪些内容」。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("rag_reference")
public class RagReference {
    @Id(keyType = KeyType.Auto)
    private Long id;
    /** 文章任务ID */
    private String taskId;
    /** 创作阶段：title/outline/content */
    private String stage;
    /** 参考源ID（文章 taskId / 文档 source） */
    private String refId;
    /** 参考类型：article/document/skill */
    private String refType;
    /** 参考标题（展示用） */
    private String refTitle;
    /** 相关度分数（重排后） */
    private Double score;
    private LocalDateTime createTime;
}
