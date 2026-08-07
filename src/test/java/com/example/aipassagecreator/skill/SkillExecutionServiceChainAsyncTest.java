package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.RagService;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SkillExecutionService.executeChainAsync 单元测试
 * <p>
 * 核心主张：链执行抛出异常（派发/图编译等灾难性错误）时退还全部 N 配额，
 * 且无论成败都在 finally 中 complete SSE + 注销链归属。
 * 链上单 skill 失败已在 executeSync 内退还，不在此方法职责内。</p>
 */
@ExtendWith(MockitoExtension.class)
class SkillExecutionServiceChainAsyncTest {

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

    private SkillExecutionService service;

    @BeforeEach
    void setUp() {
        service = new SkillExecutionService(sseEmitterManager, executionRegistry,
                quotaService, userService, skillRegistry, ragService);
    }

    @Test
    @DisplayName("链异常 → 退还全部 N 配额 + complete SSE + 注销归属")
    void executeChainAsync_chainThrows_refundsAllQuotaAndCleansUp() {
        SkillExecutionChain chain = mock(SkillExecutionChain.class);
        when(chain.getChainExecutionId()).thenReturn("chain-1");
        when(chain.executeSync(any(), any(), any()))
                .thenThrow(new RuntimeException("图编译失败"));

        User user = new User();
        user.setId(5L);
        when(userService.getById(5L)).thenReturn(user);

        service.executeChainAsync(chain, Map.of(), 5L, 3);

        verify(quotaService, times(3)).refundQuota(user);
        verify(sseEmitterManager).complete("chain-1");
        verify(executionRegistry).unregisterChain("chain-1");
    }

    @Test
    @DisplayName("链成功 → 不退还配额，仅 complete SSE + 注销归属")
    void executeChainAsync_success_noRefundAndCleansUp() {
        SkillExecutionChain chain = mock(SkillExecutionChain.class);
        when(chain.getChainExecutionId()).thenReturn("chain-2");
        when(chain.executeSync(any(), any(), any()))
                .thenReturn(new SkillExecutionChain.ChainResult(Map.of("topic-gen", "AI"), null));

        service.executeChainAsync(chain, Map.of(), 5L, 2);

        verify(quotaService, never()).refundQuota(any());
        verify(sseEmitterManager).complete("chain-2");
        verify(executionRegistry).unregisterChain("chain-2");
    }

    @Test
    @DisplayName("链异常但退款目标用户不存在 → 不调用 refundQuota，仍完成收尾")
    void executeChainAsync_userMissing_refundSkippedButCleanupStillRuns() {
        SkillExecutionChain chain = mock(SkillExecutionChain.class);
        when(chain.getChainExecutionId()).thenReturn("chain-3");
        when(chain.executeSync(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        when(userService.getById(9L)).thenReturn(null);

        service.executeChainAsync(chain, Map.of(), 9L, 2);

        verify(quotaService, never()).refundQuota(any());
        verify(sseEmitterManager).complete("chain-3");
        verify(executionRegistry).unregisterChain("chain-3");
    }
}
