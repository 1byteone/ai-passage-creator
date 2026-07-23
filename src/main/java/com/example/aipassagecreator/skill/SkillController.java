package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.dto.skill.SkillConfirmRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteResponse;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
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
    private final SkillExecutionService skillExecutionService;
    private final SkillExecutionMapper skillExecutionMapper;

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

        // 异步执行（通过 SkillExecutionService 确保 @Async 生效）
        skillExecutionService.executeAsync(
                skillName,
                request.getInputs(),
                msg -> sseEmitterManager.send(execution.getExecutionId(), msg),
                loginUser.getId()
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
    public SseEmitter progress(@PathVariable String executionId, HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        if (loginUser == null) {
            return null;
        }
        // 验证执行记录属于当前用户
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("skill_execution_id", executionId));
        if (po == null || !po.getUserId().equals(loginUser.getId())) {
            return null;
        }
        return sseEmitterManager.createEmitter(executionId);
    }

    /**
     * 多轮交互确认
     */
    @PostMapping("/{executionId}/confirm")
    public BaseResponse<?> confirm(
            @PathVariable String executionId,
            @RequestBody SkillConfirmRequest request,
            HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        if (loginUser == null) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 验证执行记录属于当前用户
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("skill_execution_id", executionId));
        if (po == null || !po.getUserId().equals(loginUser.getId())) {
            return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "无权操作此执行记录");
        }
        log.info("Skill 确认: executionId={}, phase={}, action={}", executionId, request.getPhase(), request.getAction());
        return ResultUtils.success("确认已接收");
    }

    /**
     * 获取 Skill 执行结果（从数据库查询）
     */
    @GetMapping("/{executionId}/result")
    public BaseResponse<?> getResult(@PathVariable String executionId, HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        if (loginUser == null) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询实际状态，并验证所有权
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("skill_execution_id", executionId)
                        .eq("user_id", loginUser.getId()));
        if (po == null) {
            return ResultUtils.success(Map.of("status", "NOT_FOUND"));
        }
        return ResultUtils.success(Map.of(
                "status", po.getStatus(),
                "skillName", po.getSkillName(),
                "phase", po.getPhase() != null ? po.getPhase() : "",
                "durationMs", po.getDurationMs(),
                "errorMessage", po.getErrorMessage() != null ? po.getErrorMessage() : ""
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