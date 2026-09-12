package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.skill.SkillDefinition;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * settle 接线契约：data-visualization-report 到达 SUCCESS 终态后才触发收尾产物。
 */
@ExtendWith(MockitoExtension.class)
class DataVizSkillSettleTest {

    @Mock private com.example.aipassagecreator.skill.SkillSseEmitterManager sseEmitterManager;
    @Mock private com.example.aipassagecreator.skill.SkillExecutionRegistry executionRegistry;
    @Mock private com.example.aipassagecreator.service.QuotaService quotaService;
    @Mock private com.example.aipassagecreator.service.UserService userService;
    @Mock private com.example.aipassagecreator.skill.SkillRegistry skillRegistry;
    @Mock private com.example.aipassagecreator.service.RagService ragService;
    @Mock private com.example.aipassagecreator.comic.ComicJournalService comicJournalService;
    @Mock private DataVizPostProcessor dataVizPostProcessor;

    @InjectMocks private SkillExecutionService service;

    private SkillExecution successExecution(String skillName, SkillExecutionPo po) {
        SkillExecution execution = mock(SkillExecution.class);
        lenient().when(execution.getStatus()).thenReturn(SkillExecutionStatusEnum.SUCCESS.getValue());
        lenient().when(execution.getPersistedOutput())
                .thenReturn(Map.of("chartSpecs", Map.of("charts", java.util.List.of())));
        lenient().when(execution.getPoForIndex()).thenReturn(po);
        SkillDefinition def = new SkillDefinition();
        def.setName(skillName);
        lenient().when(execution.getDefinition()).thenReturn(def);
        return execution;
    }

    @Test
    @DisplayName("SUCCESS → 触发 dataVizPostProcessor.processAsync，且不误触 comic-journal")
    void settle_success_triggersDataVizPostProcessor() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setStatus(SkillExecutionStatusEnum.SUCCESS.getValue());
        po.setSkillExecutionId("exec-dataviz-1");
        po.setSkillName("data-visualization-report");
        po.setInputData("{\"rawData\":\"a,b\\n1,2\\n\",\"dataFormat\":\"csv\"}");

        service.executeAsync(successExecution("data-visualization-report", po), 1L);

        verify(dataVizPostProcessor).processAsync(eq(po), any());
        verify(comicJournalService, never()).processAsync(any(), any(), any());
    }

    @Test
    @DisplayName("其他 Skill SUCCESS → 不触发 dataviz 收尾")
    void settle_success_otherSkill_doesNotTriggerDataViz() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setStatus(SkillExecutionStatusEnum.SUCCESS.getValue());

        service.executeAsync(successExecution("proofreading", po), 1L);

        verify(dataVizPostProcessor, never()).processAsync(any(), any());
    }
}
