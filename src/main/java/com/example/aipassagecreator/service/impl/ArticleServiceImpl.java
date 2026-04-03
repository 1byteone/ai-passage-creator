package com.example.aipassagecreator.service.impl;

import cn.hutool.core.util.IdUtil;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.dto.article.ArticleQueryRequest;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ArticleVO;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.utils.GsonUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.aipassagecreator.constant.UserConstant.ADMIN_ROLE;

@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {


    @Override
    public  String createArticle(String topic, User loginUser){
        //生成任务ID
        String taskId = IdUtil.simpleUUID();

        //创建文章记录
        Article article = Article.builder()
                .taskId(taskId)
                .userId(loginUser.getId())
                .topic(topic)
                .status(ArticleStatusEnum.PENDING.getValue())
                .createTime(LocalDateTime.now())
                .build();

        save(article);

        log.info("文章任务已创建，taskId={},userId={}",taskId,loginUser.getId());
        return taskId;
    }

    @Override
    public Article getByTaskId(String taskId){
        return this.getOne(
                QueryWrapper.create().eq(Article::getTaskId,taskId)
        );
    }

    @Override
    public void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage){
        Article article = getByTaskId(taskId);

        if(article == null){
            log.error("文章任务不存在，taskId={}",taskId);
            return;
        }

        article.setStatus(status.getValue());
        article.setErrorMessage(errorMessage);
        this.updateById(article);

        log.info("文章任务状态已更新，taskId={},status={}",taskId,status.getValue());
    }

    @Override
    public void saveArticleContent(String taskId, ArticleState state){
        Article article = getByTaskId(taskId);

        if(article == null){
            log.error("文章任务不存在，taskId={}",taskId);
            return;
        }

        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());

        //保存封面图URL(从 images 列表中提取 position=1 的URL)
        if(state.getImages() != null && !state.getImages().isEmpty()){
            ArticleState.ImageResult coverImage = state.getImages().stream()
                    .filter(img -> img.getPosition()!=null && img.getPosition() == 1)
                    .findFirst()
                    .orElse(null);
            if(coverImage != null && coverImage.getUrl() != null){
                article.setCoverImage(coverImage.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());

        this.updateById(article);
        log.info("文章内容已保存，taskId={}",taskId);
    }

    @Override
    public Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser){
        long current = request.getCurrent();
        long size = request.getPageSize();

        //构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(Article::getIsDelete, 0)
                .orderBy(Article::getCreateTime, false);

        //非管理员只能查看自己的文章
        if(!ADMIN_ROLE.equals(loginUser.getUserRole())){
            queryWrapper.eq(Article::getUserId, loginUser.getId());
        }else if(request.getUserId() != null){
            queryWrapper.eq(Article::getUserId, request.getUserId());
        }

        //按状态筛选
        if(request.getStatus() != null&&!request.getStatus().trim().isEmpty()){
            queryWrapper.eq(Article::getStatus, request.getStatus());
        }

        //分页查询
        Page<Article> articlePage = this.page(new Page<>(current, size), queryWrapper);

        //转换为VO
        return convertToVOPage(articlePage);
    }

    /**
     * 将文章分页结果转换为 VO 分页
     *
     * @param articlePage 文章分页
     * @return VO 分页
     */
    private Page<ArticleVO> convertToVOPage(Page<Article> articlePage) {
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        articleVOPage.setRecords(articleVOList);

        return articleVOPage;
    }

    @Override
    public boolean deleteArticle(Long id, User loginUser){
        Article article = this.getById(id);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR);

        //权限校验，只能删除自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        //逻辑删除
        return this.removeById( id);
    }

    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限：只能查看自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        return ArticleVO.objToVo(article);
    }

    /**
     * 校验文章权限
     *
     * @param article   文章
     * @param loginUser 当前用户
     */
    private void checkArticlePermission(Article article, User loginUser) {
        if (!article.getUserId().equals(loginUser.getId()) &&
                !ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
}
