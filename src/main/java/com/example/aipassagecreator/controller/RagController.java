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
        private String type;      // article | skill，可选
        private Integer topK;     // 1-20，默认 5
    }

    /** 允许的 type 值（白名单，防注入/跨租户泄漏） */
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("article", "skill");

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
        // type 白名单过滤（非 article/skill 的传参视为 null，由 FilterExpressionBuilder 安全处理）
        String type = ALLOWED_TYPES.contains(request.getType()) ? request.getType() : null;
        return ResultUtils.success(ragService.search(request.getQuery(), type, userId, topK));
    }
}
