package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SkillConfirmationReaper DB 驱动收割测试
 * <p>
 * 收割器直接扫 DB 中超时的 AWAITING_CONFIRMATION 行（不再依赖进程内注册表），
 * 应用重启后的孤儿行同样能被收割；条件更新抢占保证只退款一次。
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Sql(scripts = {
        "classpath:skills/test-skill-execution-schema.sql",
        "classpath:skills/cleanup-skill-tables.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:skills/cleanup-skill-tables.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SkillConfirmationReaperTest {

    @Autowired
    private SkillConfirmationReaper reaper;

    @Autowired
    private SkillExecutionMapper skillExecutionMapper;

    /** 用 mock 替换 SkillExecutionService，验证退款只被调用一次 */
    @MockitoBean
    private SkillExecutionService skillExecutionService;

    private void insertAwaitingRow(String executionId, long userId, LocalDateTime updateTime) {
        skillExecutionMapper.insert(SkillExecutionPo.builder()
                .skillExecutionId(executionId)
                .skillName("research")
                .userId(userId)
                .status(SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue())
                .phase("summary")
                .createTime(updateTime)
                .updateTime(updateTime)
                .build());
    }

    private String statusOf(String executionId) {
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", executionId));
        return po.getStatus();
    }

    @Test
    @DisplayName("过时 AWAITING 行被收割为 FAILED 并退款一次，重复收割不双退")
    void staleAwaitingRow_isReapedAndRefundedOnce() {
        insertAwaitingRow("reap-1", 888L, LocalDateTime.now().minusMinutes(11));

        reaper.reapExpiredConfirmations();

        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", "reap-1"));
        assertEquals(SkillExecutionStatusEnum.FAILED.getValue(), po.getStatus(), "超时行应被收割为 FAILED");
        assertEquals("等待确认超时，已自动取消", po.getErrorMessage());
        verify(skillExecutionService).refundQuietly(888L, "reap-1");

        // 再次收割：行已 FAILED，不应再次命中 → 只退款一次
        reaper.reapExpiredConfirmations();
        verify(skillExecutionService, times(1)).refundQuietly(888L, "reap-1");
    }

    @Test
    @DisplayName("未过期的 AWAITING 行不被收割")
    void freshRow_notReaped() {
        insertAwaitingRow("reap-2", 999L, LocalDateTime.now());

        reaper.reapExpiredConfirmations();

        assertEquals(SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue(), statusOf("reap-2"),
                "未过期行应保持等待确认");
        verifyNoInteractions(skillExecutionService);
    }

    @Test
    @DisplayName("用户已 confirm 抢占为 RUNNING 的行，收割器跳过且不退款")
    void claimedRow_skipped() {
        insertAwaitingRow("reap-3", 1000L, LocalDateTime.now().minusMinutes(11));

        // 模拟用户 confirm 的原子抢占（AWAITING → RUNNING）
        SkillExecutionPo claim = new SkillExecutionPo();
        claim.setStatus(SkillExecutionStatusEnum.RUNNING.getValue());
        int claimed = skillExecutionMapper.updateByQuery(claim, QueryWrapper.create()
                .eq("skill_execution_id", "reap-3")
                .eq("status", SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue()));
        assertEquals(1, claimed, "confirm 应成功抢占");

        reaper.reapExpiredConfirmations();

        assertEquals(SkillExecutionStatusEnum.RUNNING.getValue(), statusOf("reap-3"),
                "已被抢占的行不应被收割器覆盖为 FAILED");
        verifyNoInteractions(skillExecutionService);
    }
}
