package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** RAG 知识库文档 — admin 上传的共享知识库条目（按 source 幂等） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("rag_document")
public class RagDocument {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String title;
    private String source;
    private String text;
    private Long userId;
    private String sourceType;
    private String domain;
    private String documentKind;
    private String status;
    private String branchName;
    private String commitSha;
    private String sourcePath;
    private String sectionPath;
    private Integer lineStart;
    private Integer lineEnd;
    private String checksum;
    private String batchId;
    private String projectKey;
    private String indexError;
    private Integer indexAttempts;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime indexedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
