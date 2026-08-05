package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.RagService;
import com.example.aipassagecreator.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** 检索请求 */
    @Data
    public static class RagSearchRequest {
        private String query;
        private String type;      // article | skill | document，可选
        private Integer topK;     // 1-20，默认 5
    }

    /** 文档上传请求（admin 专属，全站共享知识库） */
    @Data
    public static class RagDocumentRequest {
        private String title;     // 文档标题（检索展示）
        private String source;    // 来源标识（幂等键，重复上传覆盖）
        private String text;      // 文档正文（≥1 字符，索引前按 30000 截断）
    }

    /** 允许的 type 值（白名单，防注入/跨租户泄漏） */
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("article", "skill", "document");

    @PostMapping("/search")
    @Operation(summary = "向量语义检索（文章/Skill）")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<RagService.RagHit>> search(@RequestBody RagSearchRequest request,
                                                        HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        // 用户隔离：普通用户只能检索自己的内容；admin 可检索全站
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        Long userId = isAdmin ? null : loginUser.getId();
        int topK = request.getTopK() == null ? 5 : request.getTopK();
        // type 白名单过滤（null/非白名单值视为 null，由 FilterExpressionBuilder 安全处理；
        // 注意 Set.of().contains(null) 会 NPE，须先判空）
        String type = request.getType() != null && ALLOWED_TYPES.contains(request.getType()) ? request.getType() : null;
        return ResultUtils.success(ragService.search(request.getQuery(), type, userId, topK));
    }

    /**
     * 上传文档入知识库（仅 admin）— 全站共享，按 source 幂等
     */
    @PostMapping("/document")
    @Operation(summary = "上传文档到共享知识库（仅管理员）")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<?> uploadDocument(@RequestBody RagDocumentRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResultUtils.error(com.example.aipassagecreator.exception.ErrorCode.PARAMS_ERROR, "文档内容不能为空");
        }
        ragService.indexDocument(request.getTitle(), request.getSource(), request.getText());
        return ResultUtils.success(true);
    }
}
