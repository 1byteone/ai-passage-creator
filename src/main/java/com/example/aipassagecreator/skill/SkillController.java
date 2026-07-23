package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.dto.skill.SkillConfirmRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteResponse;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRegistry skillRegistry;
    private final SseEmitterManager sseEmitterManager;
    private final UserService userService;

    /**
     * 执行 Skill
     */
    @PostMapping("/{skillName}/execute")
    public BaseResponse<?> executeSkill(
            @PathVariable String skillName,
            @RequestBody SkillExecuteRequest request,
            HttpServletRequest servletRequest) {

        SkillDefinition def = skillRegistry.getSkill(skillName);

        // 权限校验：检查用户角色
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        if (loginUser == null) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
        }
        List<String> requiredRoles = def.getRequiredRoles();
        if (requiredRoles != null && !requiredRoles.isEmpty()) {
            boolean hasRole = requiredRoles.stream()
                    .anyMatch(role -> "admin".equals(role) && "admin".equals(loginUser.getUserRole())
                            || "vip".equals(role) && "vip".equals(loginUser.getUserRole())
                            || "user".equals(role));
            if (!hasRole) {
                return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "需要 " + requiredRoles + " 角色才能使用此 Skill");
            }
        }

        SkillExecution execution = skillRegistry.createExecution(skillName, request.getInputs());

        // 创建 SSE 连接
        sseEmitterManager.createEmitter(execution.getExecutionId());

        // 异步执行
        execution.executeAsync(
                msg -> sseEmitterManager.send(execution.getExecutionId(), msg),
                null // userId 待从 session 获取
        );

        SkillExecuteResponse response = SkillExecuteResponse.builder()
                .skillExecutionId(execution.getExecutionId())
                .skillName(skillName)
                .status("RUNNING")
                .totalPhases(def.getPhases().size())
                .progressUrl("/skill/" + execution.getExecutionId() + "/progress")
                .build();

        return ResultUtils.success(response);
    }

    /**
     * SSE 进度推送
     */
    @GetMapping("/{executionId}/progress")
    public SseEmitter progress(@PathVariable String executionId) {
        return sseEmitterManager.createEmitter(executionId);
    }

    /**
     * 多轮交互确认
     */
    @PostMapping("/{executionId}/confirm")
    public BaseResponse<String> confirm(
            @PathVariable String executionId,
            @RequestBody SkillConfirmRequest request) {
        // 现阶段返回确认已接收，实际逻辑在后续实现
        log.info("Skill 确认: executionId={}, phase={}, action={}", executionId, request.getPhase(), request.getAction());
        return ResultUtils.success("确认已接收");
    }

    /**
     * 获取 Skill 执行结果
     */
    @GetMapping("/{executionId}/result")
    public BaseResponse<Map<String, Object>> getResult(@PathVariable String executionId) {
        // 简化：从 SkillContext 获取共享数据
        var ctx = SkillContext.get(executionId);
        if (ctx == null) {
            return ResultUtils.success(Map.of("status", "NOT_FOUND"));
        }
        return ResultUtils.success(Map.of(
                "status", "RUNNING",
                "phase", ctx.getCurrentPhase(),
                "sharedData", ctx.getSharedData()
        ));
    }

    /**
     * 列出所有可用 Skill
     */
    @GetMapping("/list")
    public BaseResponse<List<Map<String, Object>>> listSkills() {
        List<Map<String, Object>> skills = skillRegistry.getAllSkills().stream()
                .map(def -> Map.<String, Object>of(
                        "name", def.getName(),
                        "description", def.getDescription(),
                        "category", def.getCategory(),
                        "phases", def.getPhases().size(),
                        "multiRound", def.isMultiRound()
                ))
                .collect(Collectors.toList());
        return ResultUtils.success(skills);
    }

    /**
     * 获取 Skill 定义详情
     */
    @GetMapping("/{skillName}/definition")
    public BaseResponse<SkillDefinition> getDefinition(@PathVariable String skillName) {
        return ResultUtils.success(skillRegistry.getSkill(skillName));
    }
}