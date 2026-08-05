package com.example.aipassagecreator.service.impl;

import cn.hutool.core.util.IdUtil;
import com.example.aipassagecreator.enums.ArticlePhaseEnum;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.dto.article.ArticleQueryRequest;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ArticleVO;
import com.example.aipassagecreator.service.ArticleAgentService;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.RagService;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.aipassagecreator.constant.UserConstant.ADMIN_ROLE;
import static com.example.aipassagecreator.constant.UserConstant.VIP_ROLE;

@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Resource
    protected QuotaService quotaService;
    @Autowired
    private ArticleAgentService articleAgentService;

    @Resource
    private RagService ragService;

    /**
     * 校验配图方式权限
     * 普通用户不能使用 NANO_BANANA 和 SVG_DIAGRAM
     */
    private void validateImageMethods(List<String> enabledImageMethods, User loginUser) {
        if (enabledImageMethods == null || enabledImageMethods.isEmpty()) {
            return;
        }

        // VIP 和管理员无限制
        if (isVipOrAdmin(loginUser)) {
            return;
        }

        // 普通用户限制
        for (String method : enabledImageMethods) {
            if (ImageMethodEnum.NANO_BANANA.getValue().equals(method) ||
                    ImageMethodEnum.AGNES.getValue().equals(method) ||
                    ImageMethodEnum.SVG_DIAGRAM.getValue().equals(method)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,
                        "高级配图功能（AI 生图、SVG 图表）仅限 VIP 会员使用");
            }
        }
    }

    /**
     * 判断是否为 VIP 或管理员
     */
    private boolean isVipOrAdmin(User user) {
        return ADMIN_ROLE.equals(user.getUserRole()) ||
                VIP_ROLE.equals(user.getUserRole());
    }

    /**
     * 处理配图方式
     * 如果用户未选择，给普通用户设置默认的非 VIP 方式，VIP 用户不限制
     */
    private List<String> processImageMethods(List<String> enabledImageMethods, User loginUser) {
        // 如果用户已选择，直接返回
        if (enabledImageMethods != null && !enabledImageMethods.isEmpty()) {
            return enabledImageMethods;
        }

        // VIP 和管理员：不限制，返回 null 表示支持所有方式
        if (isVipOrAdmin(loginUser)) {
            return null;
        }

        // 普通用户：返回默认的非 VIP 方式
        return List.of(
                ImageMethodEnum.PEXELS.getValue(),
                ImageMethodEnum.MERMAID.getValue(),
                ImageMethodEnum.ICONIFY.getValue(),
                ImageMethodEnum.EMOJI_PACK.getValue()
        );
    }

    @Override
    public String createArticleTask(String topic, String style, String methodology,
                                    List<String> enabledImageMethods, User loginUser) {
        // 处理配图方式：如果用户未选择，给普通用户设置默认的非 VIP 方式
        List<String> finalImageMethods = processImageMethods(enabledImageMethods, loginUser);

        // 校验配图方式权限（普通用户不能使用 NANO_BANANA 和 SVG_DIAGRAM）
        validateImageMethods(finalImageMethods, loginUser);

        // 生成任务ID
        String taskId = IdUtil.simpleUUID();

        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStyle(style);
        article.setMethodology(methodology == null || methodology.isBlank() ? "default" : methodology);
        article.setEnabledImageMethods(finalImageMethods != null && !finalImageMethods.isEmpty()
                ? GsonUtils.toJson(finalImageMethods) : null);
        article.setStatus(ArticleStatusEnum.PENDING.getValue());
        article.setPhase(ArticlePhaseEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());

        this.save(article);

        log.info("文章任务已创建, taskId={}, userId={}, style={}, methodology={}",
                taskId, loginUser.getId(), style, article.getMethodology());
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArticleTaskWithQuotaCheck(String topic, String style, String methodology,
                                                  List<String> enabledImageMethods, User loginUser) {
        //在同一个事务中：先扣配额，再创建任务
        //如果任务创建失败，配额会自动回滚
        quotaService.checkAndConsumeQuota(loginUser);
        return createArticleTask(topic, style, methodology, enabledImageMethods, loginUser);
    }

    @Override
    public void confirmTitle(String taskId,String mainTitle , String subTitle,String userDescription,User loginUser){
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        //校验权限
        checkArticlePermission(article,loginUser);

        //校验当前阶段（必须是 TITLE_SELECTING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(!currentPhase.equals(ArticlePhaseEnum.TITLE_SELECTING),ErrorCode.OPERATION_ERROR,"当前阶段不允许此操作");

        //保存用户选择的标题和补充描述
        article.setMainTitle(mainTitle);
        article.setSubTitle(subTitle);
        article.setUserDescription(userDescription);
        article.setPhase(ArticlePhaseEnum.OUTLINE_GENERATING.getValue());

        this.updateById(article);
        log.info("用户确认标题，taskId={},mainTitle={}",taskId,mainTitle);
    }

    @Override
    public void confirmOutline(String taskId,List<ArticleState.OutlineSection>  outline,User loginUser){
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        //校验权限
        checkArticlePermission(article,loginUser);

        //校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(!currentPhase.equals(ArticlePhaseEnum.OUTLINE_EDITING),ErrorCode.OPERATION_ERROR,"当前阶段不允许此操作");

        //保存用户编辑后的大纲
        article.setOutline(GsonUtils.toJson(outline));
        article.setPhase(ArticlePhaseEnum.CONTENT_GENERATING.getValue());

        this.updateById(article);
        log.info("用户确认大纲，taskId={},sectionsCount={}",taskId,outline.size());
    }

    @Override
    public void updatePhase(String taskId, ArticlePhaseEnum phase){
        Article article = getByTaskId(taskId);
        if(article == null){
            log.error("文章不存在，taskId={}",taskId);
            return;
        }

        article.setPhase(phase.getValue());
        this.updateById(article);
        log.info("文章阶段已更新，taskId={},phase={}",taskId,phase.getValue());
    }

    @Override
    public void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions){
        Article article = getByTaskId(taskId);
        if(article == null){
            log.error("文章不存在，taskId={}",taskId);
            return;
        }

        article.setTitleOptions(GsonUtils.toJson(titleOptions));
        this.updateById(article);
        log.info("文章标题选项已保存，taskId={},optionsCount={}",taskId,titleOptions.size());
    }

    @Override
    public List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion , User loginUser){
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        //校验权限
        checkArticlePermission(article,loginUser);

        ThrowUtils.throwIf(!isVipOrAdmin(loginUser),ErrorCode.NO_AUTH_ERROR,"AI 修改大纲功能仅限 VIP 会员使用");

        //校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(!currentPhase.equals(ArticlePhaseEnum.OUTLINE_EDITING),ErrorCode.OPERATION_ERROR,"当前阶段不允许此操作");

        //获取当前大纲
        List<ArticleState.OutlineSection> currentOutline = GsonUtils.fromJson(
                article.getOutline(), new TypeToken<List<ArticleState.OutlineSection>>() {} );

        //调用 AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleAgentService.aiModifyOutline(
                article.getMainTitle(),
                article.getSubTitle(),
                currentOutline,
                modifySuggestion
        );

        //保存修改后的大纲
        article.setOutline(GsonUtils.toJson(modifiedOutline));
        this.updateById(article);

        log.info("AI修改大纲完成，taskId={},sectionsCount={}",taskId,modifiedOutline.size());
        return modifiedOutline;
    }

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
        boolean result = this.removeById(id);
        // 同步清理 RAG 向量（异步，失败静默）
        if (result && article.getTaskId() != null) {
            ragService.deleteByTaskIdAsync(article.getTaskId());
        }
        return result;
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
