package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.enums.UserRoleEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * 报告产物查询（HTML/PNG）。
 * <p>
 * 产物由 {@link DataVizPostProcessor} 在 Skill SUCCESS 后异步落盘，查询时可能尚未就绪：
 * 就绪状态走 {@code /artifact}（200 + ready 标志），单文件端点则直接返回未就绪错误。
 * 归属校验按 {@code skill_execution.user_id} 判定，admin 放行。
 */
@RestController
@RequestMapping("/dataviz")
@RequiredArgsConstructor
@Tag(name = "DataVizController", description = "数据图表报告")
public class DataVizController {

    private static final String HTML_FILE = "report.html";
    private static final String PNG_FILE = "report.png";

    private final DataVizStorageService storage;
    private final SkillExecutionMapper executionMapper;
    private final UserService userService;

    @GetMapping("/{executionId}/artifact")
    @Operation(summary = "查询报告产物就绪状态")
    public BaseResponse<DataVizArtifactVO> getArtifact(@PathVariable String executionId,
                                                      HttpServletRequest request) {
        Path dir = artifactDirOf(executionId, request);
        return ResultUtils.success(new DataVizArtifactVO(
                storage.exists(dir, HTML_FILE),
                storage.exists(dir, PNG_FILE),
                "/api/dataviz/" + executionId + "/html",
                "/api/dataviz/" + executionId + "/png"));
    }

    @GetMapping("/{executionId}/html")
    @Operation(summary = "报告 HTML")
    public ResponseEntity<byte[]> getHtml(@PathVariable String executionId,
                                          HttpServletRequest request) {
        Path dir = artifactDirOf(executionId, request);
        byte[] body = storage.read(dir, HTML_FILE);
        if (body == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "报告尚未生成，请稍后刷新");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
    }

    @GetMapping("/{executionId}/png")
    @Operation(summary = "报告 PNG")
    public ResponseEntity<byte[]> getPng(@PathVariable String executionId,
                                         HttpServletRequest request) {
        Path dir = artifactDirOf(executionId, request);
        byte[] body = storage.read(dir, PNG_FILE);
        if (body == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "PNG 尚未导出或渲染引擎不可用");
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(body);
    }

    /**
     * 先过路径白名单再查归属：非法标识在任何 DB 访问前就被拒，{@code ../} 无从进入执行记录查询。
     */
    private Path artifactDirOf(String executionId, HttpServletRequest request) {
        Path dir = storage.artifactDir(executionId);
        ownedExecution(executionId, request);
        return dir;
    }

    private SkillExecutionPo ownedExecution(String executionId, HttpServletRequest request) {
        // 先认证再查记录：未登录必须报 40100，而不是被归属查询的 40400 掩盖
        User loginUser = userService.getLoginUser(request);
        // skill_execution_id 是业务唯一键而非主键（主键为自增 Long id），故按列查询
        SkillExecutionPo po = executionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", executionId));
        if (po == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "报告不存在");
        }
        boolean admin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        if (!admin && !loginUser.getId().equals(po.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该报告");
        }
        return po;
    }
}
