package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.dto.skill.SkillChainExecuteRequest;
import com.example.aipassagecreator.model.dto.skill.SkillConfirmRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteResponse;
import com.example.aipassagecreator.model.dto.skill.SkillExecutionQueryRequest;
import com.example.aipassagecreator.model.dto.skill.SkillResultResponse;
import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.enums.UserRoleEnum;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.model.vo.SkillExecutionVO;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillController {

    private static final List<String> PUBLIC_SKILLS =
            List.of("topic-gen", "proofreading", "article-to-x", "research",
                    "seo-optimizer", "content-translator", "ai-detox", "seeding-copy");

    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_MODIFY = "modify";
    private static final String ACTION_RETRY = "retry";

    private final SkillRegistry skillRegistry;
    private final SkillSseEmitterManager sseEmitterManager;
    private final UserService userService;
    private final SkillExecutionService skillExecutionService;
    private final SkillExecutionMapper skillExecutionMapper;
    private final QuotaService quotaService;
    private final SkillExecutionRegistry executionRegistry;

    /**
     * 执行 Skill
     */
    @PostMapping("/{skillName}/execute")
    @RateLimit(limit = 10, window = 60, unit = TimeUnit.SECONDS, key = "skill_execute")
    public BaseResponse<?> executeSkill(
            @PathVariable String skillName,
            @RequestBody SkillExecuteRequest request,
            HttpServletRequest servletRequest) {

        SkillDefinition def = getPublicSkill(skillName);

        // 权限校验：检查用户角色（未登录时 getLoginUser 抛出 NOT_LOGIN_ERROR）
        User loginUser = userService.getLoginUser(servletRequest);
        List<String> requiredRoles = def.getRequiredRoles();
        if (requiredRoles != null && !requiredRoles.isEmpty()) {
            // 满足任一所需角色即可（admin 豁免 + vip 为 user 超集，与 AuthInterceptor 同语义）
            boolean hasRole = requiredRoles.stream()
                    .anyMatch(role -> UserRoleEnum.satisfies(loginUser.getUserRole(), role));
            if (!hasRole) {
                return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "需要 " + requiredRoles + " 角色才能使用此 Skill");
            }
        }

        // 配额校验与扣减：每次执行消耗 1 配额，admin/VIP 豁免（与文章生成同规则）
        quotaService.checkAndConsumeQuota(loginUser, "配额不足，无法执行此 Skill");

        Map<String, Object> inputs = request == null || request.getInputs() == null
                ? Map.of()
                : request.getInputs();

        SkillExecution execution;
        try {
            execution = skillRegistry.createExecution(skillName, inputs);
            execution.prepare(loginUser.getId());
            // 含确认阶段的 Skill 需登记实例，confirm 时才能取回并从检查点续跑
            if (skillRegistry.hasConfirmationPhase(skillName)) {
                executionRegistry.register(execution, loginUser.getId());
            }
            // 异步执行（通过 SkillExecutionService 确保 @Async 生效）
            skillExecutionService.executeAsync(execution, loginUser.getId());
        } catch (Exception e) {
            // 派发失败说明未真正消耗算力，退还配额避免白扣
            log.error("Skill 派发失败，退还配额: skillName={}, userId={}", skillName, loginUser.getId(), e);
            quotaService.refundQuota(loginUser);
            throw e;
        }

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
     * 链式执行多个 Skill — 前一个输出作为后一个输入
     * <p>
     * 注意：链式编排为同步执行，HTTP 请求会阻塞至全部完成。
     * 每次链式执行消耗 N 个配额（N = skill 数量）。
     */
    @PostMapping("/chain/execute")
    @Operation(summary = "链式执行多个 Skill")
    @RateLimit(limit = 5, window = 60, unit = TimeUnit.SECONDS, key = "skill_chain")
    public BaseResponse<?> executeChain(@RequestBody SkillChainExecuteRequest request,
                                        HttpServletRequest servletRequest) {
        if (request == null || request.getSkillNames() == null || request.getSkillNames().size() < 2) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "链式编排至少需要 2 个 skill");
        }

        // 校验所有 skill 均已公开
        for (String skillName : request.getSkillNames()) {
            if (!PUBLIC_SKILLS.contains(skillName)) {
                return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "Skill 暂未公开: " + skillName);
            }
        }

        // 权限 + 配额（每个 skill 消耗 1 配额）
        User loginUser = userService.getLoginUser(servletRequest);
        int requiredQuota = request.getSkillNames().size();
        for (int i = 0; i < requiredQuota; i++) {
            quotaService.checkAndConsumeQuota(loginUser, "配额不足，链式编排需 " + requiredQuota + " 配额");
        }

        Map<String, Object> inputs = request.getInputs() == null ? Map.of() : request.getInputs();
        String chainId = "chain-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            SkillExecutionChain chain = skillRegistry.createChain(
                    chainId, request.getSkillNames().toArray(new String[0]));
            Map<String, Object> outputs = chain.executeSync(
                    msg -> sseEmitterManager.publish(chainId, msg),
                    inputs,
                    loginUser.getId());

            return ResultUtils.success(Map.of(
                    "chainId", chainId,
                    "skills", request.getSkillNames(),
                    "outputs", outputs));
        } catch (Exception e) {
            log.error("链式编排失败: skills={}", request.getSkillNames(), e);
            // 失败退还配额（未全部完成）
            for (int i = 0; i < requiredQuota; i++) {
                quotaService.refundQuota(loginUser);
            }
            throw e;
        }
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
     * <p>
     * 仅在执行暂停于 AWAITING_CONFIRMATION 时可调用。
     * 支持 approve（直接续跑）、modify（写入修改后再续跑）与
     * retry（清除当前阶段输出，重新生成该阶段）。
     */
    @PostMapping("/{executionId}/confirm")
    public BaseResponse<?> confirm(
            @PathVariable String executionId,
            @RequestBody SkillConfirmRequest request,
            HttpServletRequest servletRequest) {
        User loginUser = userService.getLoginUser(servletRequest);

        // 验证执行记录属于当前用户
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", executionId));
        if (po == null || !po.getUserId().equals(loginUser.getId())) {
            return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "无权操作此执行记录");
        }
        if (!SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue().equals(po.getStatus())) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR,
                    "当前状态不允许确认: " + po.getStatus());
        }

        String action = request == null || request.getAction() == null
                ? ACTION_APPROVE
                : request.getAction().trim().toLowerCase();
        if (!ACTION_APPROVE.equals(action) && !ACTION_MODIFY.equals(action) && !ACTION_RETRY.equals(action)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR,
                    "暂不支持的确认动作: " + action + "，当前支持 approve / modify / retry");
        }

        // 检查点存于进程内，应用重启后执行实例会丢失
        SkillExecution execution = executionRegistry.get(executionId);
        if (execution == null) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "执行已过期，请重新发起");
        }

        Map<String, Object> modifiedData = null;
        if (ACTION_MODIFY.equals(action)) {
            modifiedData = parseModifiedData(request.getModifiedData());
            if (modifiedData == null || modifiedData.isEmpty()) {
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "modify 动作需提供 modifiedData");
            }
        }

        // 原子抢占：在同步路径内就把状态从 AWAITING_CONFIRMATION 翻成 RUNNING。
        // 若留给异步的 resume() 去翻，SkillConfirmationReaper 的条件更新会在这个窗口内
        // 照样命中，把用户刚确认的执行判为超时并退款；并发重复 confirm 也靠这一步拦住。
        SkillExecutionPo claim = new SkillExecutionPo();
        claim.setStatus(SkillExecutionStatusEnum.RUNNING.getValue());
        int claimed = skillExecutionMapper.updateByQuery(claim, QueryWrapper.create()
                .eq("skill_execution_id", executionId)
                .eq("status", SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue()));
        if (claimed == 0) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "确认已被处理，或执行已超时取消");
        }

        log.info("Skill 确认: executionId={}, phase={}, action={}", executionId, po.getPhase(), action);
        skillExecutionService.resumeAsync(execution, loginUser.getId(), modifiedData,
                ACTION_RETRY.equals(action));

        return ResultUtils.success(Map.of(
                "skillExecutionId", executionId,
                "action", action,
                "status", SkillExecutionStatusEnum.RUNNING.getValue()));
    }

    /**
     * 解析 modify 动作携带的修改数据（JSON 对象字符串）
     *
     * @return 解析失败返回 null
     */
    private Map<String, Object> parseModifiedData(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GsonUtils.fromJson(raw, new TypeToken<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("modifiedData 解析失败: {}", e.getMessage());
            return null;
        }
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
     * 分页查询 Skill 执行历史
     * <p>
     * 普通用户仅能查看本人记录，管理员可查看全部。
     */
    @PostMapping("/executions")
    public BaseResponse<Page<SkillExecutionVO>> listExecutions(
            @RequestBody(required = false) SkillExecutionQueryRequest request,
            HttpServletRequest servletRequest) {
        User loginUser = userService.getLoginUser(servletRequest);
        SkillExecutionQueryRequest query = request == null ? new SkillExecutionQueryRequest() : request;

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("is_delete", 0)
                .orderBy("create_time", false);

        // 非管理员只能查看自己的执行记录
        if (!UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole())) {
            queryWrapper.eq("user_id", loginUser.getId());
        }
        if (query.getSkillName() != null && !query.getSkillName().isBlank()) {
            queryWrapper.eq("skill_name", query.getSkillName());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            queryWrapper.eq("status", query.getStatus());
        }

        Page<SkillExecutionPo> poPage = skillExecutionMapper.paginate(
                new Page<>(query.getCurrent(), query.getPageSize()), queryWrapper);

        Page<SkillExecutionVO> voPage = new Page<>();
        voPage.setPageNumber(poPage.getPageNumber());
        voPage.setPageSize(poPage.getPageSize());
        voPage.setTotalRow(poPage.getTotalRow());
        voPage.setRecords(poPage.getRecords().stream()
                .map(SkillExecutionVO::objToVo)
                .collect(Collectors.toList()));

        return ResultUtils.success(voPage);
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
