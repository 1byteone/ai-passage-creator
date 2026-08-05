package com.example.aipassagecreator.mapper;

import com.example.aipassagecreator.model.po.Article;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 统计全平台热门选题（按 topic 去重计数，COMPLETED 优先）
     *
     * @param limit 返回条数
     * @return 每项为 {topic, cnt} 的列表，按出现次数降序
     */
    @Select("SELECT topic AS topic, COUNT(*) AS cnt FROM article " +
            "WHERE isDelete = 0 AND status = 'COMPLETED' AND topic IS NOT NULL AND topic <> '' " +
            "GROUP BY topic ORDER BY cnt DESC, MAX(createTime) DESC LIMIT #{limit}")
    List<Map<String, Object>> countTopicsByPopularity(@Param("limit") int limit);
}
