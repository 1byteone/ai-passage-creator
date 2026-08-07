package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.RagService;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillExecutionRagIndexTest {

    @Mock
    private SkillSseEmitterManager sseEmitterManager;
    @Mock
    private SkillExecutionRegistry executionRegistry;
    @Mock
    private QuotaService quotaService;
    @Mock
    private UserService userService;
    @Mock
    private SkillRegistry skillRegistry;
    @Mock
    private RagService ragService;

    @InjectMocks
    private SkillExecutionService service;

    @Test
    @DisplayName("P1 — Skill 到达 SUCCESS 终态后触发 indexSkillAsync")
    void settle_success_triggersIndexSkill() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setStatus(SkillExecutionStatusEnum.SUCCESS.getValue());
        po.setOutputData("{\"result\":\"调研结论\"}");

        SkillExecution execution = mockSuccessExecution(po);
        // settle 是 private，经 executeAsync 触发（mock execute 不抛异常）
        service.executeAsync(execution, 1L);

        ArgumentCaptor<SkillExecutionPo> poCaptor = ArgumentCaptor.forClass(SkillExecutionPo.class);
        ArgumentCaptor<Map> outCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ragService).indexSkillAsync(poCaptor.capture(), outCaptor.capture());
        assertTrue(outCaptor.getValue().containsKey("result"));
    }

    private SkillExecution mockSuccessExecution(SkillExecutionPo po) {
        SkillExecution execution = org.mockito.Mockito.mock(SkillExecution.class);
        when(execution.getStatus())
                .thenReturn(SkillExecutionStatusEnum.SUCCESS.getValue());
        when(execution.getPersistedOutput())
                .thenReturn(Map.of("result", "调研结论"));
        when(execution.getPoForIndex()).thenReturn(po);
        return execution;
    }
}
