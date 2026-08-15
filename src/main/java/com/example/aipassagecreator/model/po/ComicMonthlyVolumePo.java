package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Table("comic_monthly_volume")
public class ComicMonthlyVolumePo {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long bookId;
    private String yearMonth;
    private Integer episodeCount;
    private String indexHtml;
    private String coverTitle;
    @Column(isLogicDelete = true)
    private Integer isDelete;
    @Column(onInsertValue = "CURRENT_TIMESTAMP")
    private LocalDateTime createTime;
    @Column(onInsertValue = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
}
