package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComicSkillSettleTest {

    @Mock private com.example.aipassagecreator.skill.SkillSseEmitterManager sseEmitterManager;
    @Mock private com.example.aipassagecreator.skill.SkillExecutionRegistry executionRegistry;
    @Mock private com.example.aipassagecreator.service.QuotaService quotaService;
    @Mock private com.example.aipassagecreator.service.UserService userService;
    @Mock private com.example.aipassagecreator.skill.SkillRegistry skillRegistry;
    @Mock private com.example.aipassagecreator.service.RagService ragService;
    @Mock private ComicJournalService comicJournalService;

    @InjectMocks private SkillExecutionService service;

    @Test
    @DisplayName("comic-journal 到达 SUCCESS 终态后触发 comicJournalService.processAsync")
    void settle_success_triggersComicJournal() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setStatus(SkillExecutionStatusEnum.SUCCESS.getValue());
        po.setSkillName("comic-journal");
        po.setOutputData("{\"layoutResult\":{}}");

        SkillExecution execution = mock(SkillExecution.class);
        when(execution.getStatus()).thenReturn(SkillExecutionStatusEnum.SUCCESS.getValue());
        when(execution.getPersistedOutput()).thenReturn(Map.of("layoutResult", Map.of()));
        when(execution.getPoForIndex()).thenReturn(po);
        when(execution.getDefinition())
                .thenReturn(defOf("comic-journal"));

        service.executeAsync(execution, 1L);

        verify(comicJournalService).processAsync(eq(po), any(), eq(1L));
    }

    private com.example.aipassagecreator.skill.SkillDefinition defOf(String name) {
        com.example.aipassagecreator.skill.SkillDefinition d =
                new com.example.aipassagecreator.skill.SkillDefinition();
        d.setName(name);
        return d;
    }
}
