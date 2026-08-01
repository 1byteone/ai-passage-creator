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
@Table("approval_record")
public class ApprovalRecord {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String articleTaskId;
    private Integer versionNo;
    private String status;
    private Long submittedBy;
    private Long reviewerId;
    private String comment;
    private LocalDateTime submitTime;
    private LocalDateTime reviewTime;
}
