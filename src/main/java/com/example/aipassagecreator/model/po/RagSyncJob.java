package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 当前项目研发知识库同步任务。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("rag_sync_job")
public class RagSyncJob {
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String projectKey;
    private String branchName;
    private String commitSha;
    private String status;
    private Integer totalFiles;
    private Integer processedFiles;
    private Integer totalSections;
    private Integer indexedSections;
    private String errorMessage;
    private Boolean active;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
