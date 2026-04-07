package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.model.dto.article.ArticleQueryRequest;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ArticleVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface ArticleService extends IService<Article> {
    String createArticle(String topic, User loginUser);

    Article getByTaskId(String taskId);

    void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage);

    void saveArticleContent(String taskId, ArticleState state);

    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser);

    boolean deleteArticle(Long id, User loginUser);

    ArticleVO getArticleDetail(String taskId, User loginUser);

    String createArticleTask(String topic, String style, List<String> enabledImageMethods, User loginUser);

    String createArticleTaskWithQuotaCheck(String topic, String style, List< String> enableImageMehodies, User loginUser);
}
