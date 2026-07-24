package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.dto.skill.SkillConfirmRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteResponse;
import com.example.aipassagecreator.model.dto.skill.SkillResultResponse;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillController {

    private static final List<String> PUBLIC_SKILLS = List.of("topic-gen", "proofreading", "article-to-x");

    private final SkillRegistry skillRegistry;
    private final SkillSseEmitterManager sseEmitterManager;
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

        SkillDefinition def = getPublicSkill(skillName);

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

        Map<String, Object> inputs = request == null || request.getInputs() == null
                ? Map.of()
                : request.getInputs();
        SkillExecution execution = skillRegistry.createExecution(skillName, inputs);
        execution.prepare(loginUser.getId());

        // 异步执行（通过 SkillExecutionService 确保 @Async 生效）
        skillExecutionService.executeAsync(execution, loginUser.getId());

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
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 验证执行记录属于当前用户
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("skill_execution_id", executionId));
        if (po == null || !po.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "执行记录不存在");
        }
        return sseEmitterManager.subscribe(executionId);
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
        Map<String, Object> outputData = po.getOutputData() == null || po.getOutputData().isBlank()
                ? Map.of()
                : GsonUtils.fromJson(po.getOutputData(), new TypeToken<Map<String, Object>>() {
                });
        Map<String, Object> inputData = po.getInputData() == null || po.getInputData().isBlank()
                ? Map.of()
                : GsonUtils.fromJson(po.getInputData(), new TypeToken<Map<String, Object>>() {
                });
        return ResultUtils.success(SkillResultResponse.builder()
                .skillExecutionId(executionId)
                .skillName(po.getSkillName())
                .status(po.getStatus())
                .phase(po.getPhase() != null ? po.getPhase() : "")
                .durationMs(po.getDurationMs())
                .errorMessage(po.getErrorMessage() != null ? po.getErrorMessage() : "")
                .inputData(inputData != null ? inputData : Map.of())
                .outputData(outputData != null ? outputData : Map.of())
                .build());
    }

    /**
     * 列出所有可用 Skill
     */
    @GetMapping("/list")
    public BaseResponse<List<Map<String, Object>>> listSkills() {
        List<Map<String, Object>> skills = PUBLIC_SKILLS.stream()
                .map(skillRegistry::getSkill)
                .map(def -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("name", def.getName());
                    summary.put("description", def.getDescription());
                    summary.put("category", def.getCategory());
                    summary.put("phases", def.getPhases().size());
                    summary.put("multiRound", def.isMultiRound());
                    return summary;
                })
                .collect(Collectors.toList());
        return ResultUtils.success(skills);
    }

    /**
     * 获取 Skill 定义详情
     */
    @GetMapping("/{skillName}/definition")
    public BaseResponse<SkillDefinition> getDefinition(@PathVariable String skillName) {
        return ResultUtils.success(getPublicSkill(skillName));
    }

    private SkillDefinition getPublicSkill(String skillName) {
        if (!PUBLIC_SKILLS.contains(skillName)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Skill 暂未公开: " + skillName);
        }
        return skillRegistry.getSkill(skillName);
    }
}
