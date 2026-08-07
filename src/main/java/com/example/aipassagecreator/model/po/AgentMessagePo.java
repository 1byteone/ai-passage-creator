package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "agent_message")
public class AgentMessagePo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属会话 */
    private Long conversationId;

    /** user / assistant */
    private String role;

    /** text / skill / error */
    private String kind;

    /** 文本内容（assistant 为 markdown） */
    private String content;

    /** skill 执行引用 / RAG 引用等元数据（JSON） */
    private String metaJson;

    /** 由 DB 默认值 CURRENT_TIMESTAMP 填充，insert 时不传 Java 值 */
    @Column(onInsertValue = "CURRENT_TIMESTAMP")
    private LocalDateTime createTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
