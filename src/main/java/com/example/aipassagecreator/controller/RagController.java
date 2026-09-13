package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.RagDocument;
import com.example.aipassagecreator.model.po.RagReference;
import com.example.aipassagecreator.model.po.RagSyncJob;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.service.RagDocumentStore;
import com.example.aipassagecreator.service.RagKnowledgeBaseService;
import com.example.aipassagecreator.service.RagKnowledgeSyncService;
import com.example.aipassagecreator.service.RagKnowledgeSyncJobService;
import com.example.aipassagecreator.service.RagReferenceStore;
import com.example.aipassagecreator.service.RagService;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RAG 向量检索 API — 相关文章推荐 / 历史参考 / 文档问答统一入口
 */
@Slf4j
@RestController
@RequestMapping("/rag")
@Tag(name = "RagController", description = "RAG 向量检索")
public class RagController {

    @Resource
    private RagService ragService;

    @Resource
    private UserService userService;

    @Resource
    private ArticleService articleService;

    @Resource
    private RagReferenceStore ragReferenceStore;

    @Resource
    private RagDocumentStore ragDocumentStore;

    @Resource
    private RagKnowledgeBaseService ragKnowledgeBaseService;

    @Resource
    private RagKnowledgeSyncService ragKnowledgeSyncService;

    @Resource
    private RagKnowledgeSyncJobService ragKnowledgeSyncJobService;

    /** 检索请求 */
    @Data
    public static class RagSearchRequest {
        @NotBlank(message = "检索词不能为空")
        @Size(max = 200, message = "检索词过长（≤200）")
        private String query;
        private String type;      // article | skill | document，可选
        @Min(value = 1, message = "topK 最小 1")
        @Max(value = 20, message = "topK 最大 20")
        private Integer topK;     // 1-20，默认 5
    }

    /** 文档上传请求（admin 专属，全站共享知识库） */
    @Data
    public static class RagDocumentRequest {
        @Size(max = 200, message = "标题过长（≤200）")
        private String title;     // 文档标题（检索展示）
        @Size(max = 512, message = "source 过长（≤512）")
        private String source;    // 来源标识（幂等键，重复上传覆盖）
        @NotBlank(message = "文档内容不能为空")
        @Size(max = 50000, message = "文档正文过长（≤50000）")
        private String text;      // 文档正文（索引前按 30000 截断）
    }

    /** 允许的 type 值（白名单，防注入/跨租户泄漏） */
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("article", "skill", "document");

    @PostMapping("/search")
    @Operation(summary = "向量语义检索（文章/Skill）")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<RagService.RagHit>> search(@Valid @RequestBody RagSearchRequest request,
                                                        HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        // 用户隔离：普通用户只能检索自己的内容；admin 可检索全站
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        Long userId = isAdmin ? null : loginUser.getId();
        int topK = request.getTopK() == null ? 5 : request.getTopK();
        // type 白名单：非白名单值直接拒绝（而非静默降为 null 放大检索范围）
        ThrowUtils.throwIf(request.getType() != null && !ALLOWED_TYPES.contains(request.getType()),
                ErrorCode.PARAMS_ERROR, "type 仅支持 article/skill/document");
        return ResultUtils.success(ragService.search(request.getQuery(), request.getType(), userId, topK));
    }

    /** 当前项目研发知识库只读混合检索：关键词命中与向量命中合并后返回引用。 */
    @PostMapping("/knowledge/search")
    @Operation(summary = "研发知识库只读混合检索")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<RagKnowledgeBaseService.KnowledgeHit>> knowledgeSearch(
            @Valid @RequestBody RagSearchRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(ragKnowledgeBaseService.search(request.getQuery(), loginUser.getId(),
                request.getTopK() == null ? 5 : request.getTopK()));
    }

    /**
     * 上传文档入知识库（仅 admin）— 全站共享，按 source 幂等
     */
    @PostMapping("/document")
    @Operation(summary = "上传文档到共享知识库（仅管理员）")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<?> uploadDocument(@Valid @RequestBody RagDocumentRequest request,
                                          HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ragDocumentStore.upsert(request.getTitle(), request.getSource(), request.getText(), loginUser.getId());
        return ResultUtils.success(true);
    }

    /** Git 工作树文档只读扫描并重建当前项目知识索引。 */
    @PostMapping("/knowledge/sync")
    @Operation(summary = "同步当前项目 Git 知识文档")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<RagSyncJob> syncKnowledge(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(ragKnowledgeSyncJobService.start(loginUser.getId()));
    }

    /** 查询当前项目知识库同步任务进度。 */
    @GetMapping("/knowledge/sync/{id}")
    @Operation(summary = "查询研发知识库同步任务")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<RagSyncJob> syncStatus(@PathVariable Long id) {
        RagSyncJob job = ragKnowledgeSyncJobService.get(id);
        ThrowUtils.throwIf(job == null, ErrorCode.NOT_FOUND_ERROR, "同步任务不存在");
        return ResultUtils.success(job);
    }

    /** 手工文档审核通过后才允许进入正式检索。 */
    @PostMapping("/document/{id}/approve")
    @Operation(summary = "审核知识文档")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> approveDocument(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(ragDocumentStore.approve(id, loginUser.getId()));
    }

    /** 知识库文档列表（仅 admin） */
    @GetMapping("/documents")
    @Operation(summary = "知识库文档列表（仅管理员）")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<RagDocument>> documents(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String keyword) {
        return ResultUtils.success(ragDocumentStore.page(pageNum, pageSize, keyword));
    }

    /** 删除知识库文档（仅 admin）— 删表行 + 清向量 */
    @DeleteMapping("/document/{id}")
    @Operation(summary = "删除知识库文档（仅管理员）")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteDocument(@PathVariable Long id) {
        return ResultUtils.success(ragDocumentStore.deleteById(id));
    }

    /**
     * 查询某文章创作时的 RAG 参考溯源（仅本人/管理员）
     */
    @GetMapping("/references/{taskId}")
    @Operation(summary = "查询文章创作时的 RAG 参考溯源")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<RagReference>> references(@PathVariable String taskId,
                                                       HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if (!isAdmin) {
            Article article = articleService.getByTaskId(taskId);
            ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");
            ThrowUtils.throwIf(!article.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权查看该文章参考");
        }
        return ResultUtils.success(ragReferenceStore.findByTaskId(taskId));
    }
}
