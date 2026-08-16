package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Table("comic_episode")
public class ComicEpisodePo {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long bookId;
    private Integer episodeNo;
    private String title;
    private String inputType;
    private String inputSummary;
    private String style;
    private String yearMonth;
    private String routeResult;
    private String storyboardResult;
    private String imagePrompts;
    private String layoutResult;
    private String pageHtml;
    private String pngUrl;
    @Column(isLogicDelete = true)
    private Integer isDelete;
    @Column(onInsertValue = "CURRENT_TIMESTAMP")
    private LocalDateTime createTime;
    @Column(onInsertValue = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
}
