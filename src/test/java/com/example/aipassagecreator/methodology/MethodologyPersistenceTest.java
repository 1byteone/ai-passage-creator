package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.model.dto.article.ArticleCreateRequest;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyPersistenceTest {

    @Autowired
    private ArticleService articleService;
    @Autowired
    private UserService userService;

    @Test
    void titleOption_hasStrategyKey() {
        ArticleState.TitleOption opt = new ArticleState.TitleOption();
        opt.setMainTitle("测试标题");
        opt.setSubTitle("测试副标题");
        opt.setStrategyKey("curiosityGap");
        assertEquals("curiosityGap", opt.getStrategyKey());
    }

    @Test
    void articleState_hasMethodology() {
        ArticleState state = new ArticleState();
        state.setMethodology("wechat");
        assertEquals("wechat", state.getMethodology());
    }

    @Test
    void articlePo_hasMethodologyColumn() throws Exception {
        Article article = new Article();
        article.setMethodology("default");
        assertEquals("default", article.getMethodology());
    }
}
