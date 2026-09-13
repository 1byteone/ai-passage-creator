package com.example.aipassagecreator.service;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.mapper.ArticleQualityMapper;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ArticleQualityGateService 单元测试
 * <p>
 * 核心主张：(1) 无 AI 味 → 不调 detox、持久化通过行；(2) AI 味未通过 + autoDetox=true → 调 ai-detox skill、
 * state 内容被替换、二次检测、持久化；(3) autoDetox=false → 不改写、持久化未通过行。
 */
@ExtendWith(MockitoExtension.class)
class ArticleQualityGateServiceTest {

    @Mock
    private SkillRegistry skillRegistry;

    @Mock
    private ArticleQualityMapper articleQualityMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private ArticleQualityGateService service;

    private static final Long USER_ID = 5L;

    @BeforeEach
    void setUp() {
        // @Value 字段在 MockitoExtension 中不自动注入，手动设置
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "autoDetoxEnabled", true);
        ReflectionTestUtils.setField(service, "detoxIntensity", "medium");
        ReflectionTestUtils.setField(service, "passThreshold", 50);
    }

    // ──────────────────────────── 无 AI 味 → 通过 ────────────────────────────

    @Test
    @DisplayName("无 AI 味内容 → 不调 detox，持久化通过，返回 passed")
    void checkAndDetox_cleanContent_passesNoDetox() {
        // 使用第一人称只是体裁内容，不应成为质量门的硬性要求
        ArticleState state = stateWith("我昨天去公园跑步了，遇到了一只很可爱的流浪猫。它蹲在长椅下面，"
                + "我蹲下来看了它好久。它喵了一声就跑掉了。有意思的是，第二天我又在同一个地方看到了它。");

        ArticleQualityGateService.GateResult result = service.checkAndDetox(state, "t1", USER_ID);

        assertTrue(result.passed(), "violations=" + result.violations());
        assertFalse(result.detoxed());
        verify(skillRegistry, never()).createExecution(anyString(), anyMap());
        verify(articleQualityMapper).insert(any(ArticleQuality.class));
    }

    // ──────────────────────────── AI 味未通过 + autoDetox → 改写 ────────────────────────────

    @Test
    @DisplayName("AI 味内容未通过 + autoDetox → 调 ai-detox，内容被替换，持久化改写后报告")
    void checkAndDetox_aiFlavoredContent_triggersDetoxAndRewrites() {
        // 构造一段含典型 AI 套话的文本（足够触发 ≥5 项违规）
        String aiText = "在当今数字化快速发展的时代，人工智能技术正在深刻地改变着我们的生活方式。"
                + "首先，AI技术提高了生产效率。其次，AI技术改善了用户体验。最后，AI技术创造了新的商业模式。"
                + "综上所述，人工智能的发展是不可避免的。值得注意的是，我们需要关注其伦理问题。"
                + "同样重要的是，我们需要加强监管。此外，相关法律法规也需要完善。因此，我们应该积极应对。"
                + "专家认为这个方案强大，未来看起来光明。";
        ArticleState state = stateWith(aiText);

        SkillExecution mockExec = mock(SkillExecution.class);
        when(mockExec.getPersistedOutput()).thenReturn(Map.of("detoxedContent", "改写后的自然文本"));
        when(skillRegistry.createExecution(eq("ai-detox"), anyMap())).thenReturn(mockExec);

        ArticleQualityGateService.GateResult result = service.checkAndDetox(state, "t2", USER_ID);

        assertTrue(result.detoxed());
        assertEquals("改写后的自然文本", state.getFullContent());
        assertEquals("改写后的自然文本", state.getContent());
        verify(skillRegistry).createExecution(eq("ai-detox"), anyMap());
        verify(mockExec).execute(any(), eq(USER_ID));
        verify(mockExec).getPersistedOutput();

        ArgumentCaptor<ArticleQuality> qualityCaptor = ArgumentCaptor.forClass(ArticleQuality.class);
        verify(articleQualityMapper).insert(qualityCaptor.capture());
        ArticleQuality quality = qualityCaptor.getValue();
        assertEquals("改写后的自然文本", quality.getArticleContentSnapshot());
        assertEquals("humanizer-zh@91f3d394", quality.getMethodologyUsed());
        assertTrue(quality.getSuggestions().contains("humanizer-zh@91f3d394"));
    }

    @Test
    @DisplayName("AI 味内容 + detox 产出等于原文 → 不替换，标记 detoxed=false")
    void checkAndDetox_detoxReturnsSameContent_noReplace() {
        String aiText = "在当今数字化快速发展的时代，人工智能技术正在深刻地改变着我们的生活方式。"
                + "首先，AI技术提高了生产效率。其次，AI技术改善了用户体验。最后，AI技术创造了新的商业模式。"
                + "综上所述，人工智能的发展是不可避免的。值得注意的是，我们需要关注其伦理问题。"
                + "同样重要的是，我们需要加强监管。此外，相关法律法规也需要完善。因此，我们应该积极应对。"
                + "专家认为这个方案强大，未来看起来光明。";
        ArticleState state = stateWith(aiText);

        SkillExecution mockExec = mock(SkillExecution.class);
        when(mockExec.getPersistedOutput()).thenReturn(Map.of("detoxedContent", aiText));
        when(skillRegistry.createExecution(eq("ai-detox"), anyMap())).thenReturn(mockExec);

        ArticleQualityGateService.GateResult result = service.checkAndDetox(state, "t3", USER_ID);

        assertFalse(result.detoxed(), "产出等于原文，不标记为已改写");
        assertEquals(aiText, state.getFullContent());
        verify(articleQualityMapper).insert(any(ArticleQuality.class));
    }

    // ──────────────────────────── autoDetox=false → 不改写 ────────────────────────────

    @Test
    @DisplayName("AI 味内容 + autoDetox=false → 不改写，持久化未通过")
    void checkAndDetox_autoDetoxDisabled_noRewrite() {
        ReflectionTestUtils.setField(service, "autoDetoxEnabled", false);
        String aiText = "在当今数字化快速发展的时代，人工智能技术正在深刻地改变着我们的生活方式。"
                + "首先，AI技术提高了生产效率。其次，AI技术改善了用户体验。最后，AI技术创造了新的商业模式。"
                + "综上所述，人工智能的发展是不可避免的。值得注意的是，我们需要关注其伦理问题。"
                + "同样重要的是，我们需要加强监管。此外，相关法律法规也需要完善。因此，我们应该积极应对。"
                + "专家认为这个方案强大，未来看起来光明。";
        ArticleState state = stateWith(aiText);

        ArticleQualityGateService.GateResult result = service.checkAndDetox(state, "t4", USER_ID);

        assertFalse(result.passed());
        assertFalse(result.detoxed());
        assertFalse(result.violations().isEmpty(), "违规列表不应为空");
        verify(skillRegistry, never()).createExecution(anyString(), anyMap());
        verify(articleQualityMapper).insert(any(ArticleQuality.class));
    }

    @Test
    @DisplayName("质量门使用配置阈值，而不是固定的 50 分")
    void checkAndDetox_usesConfiguredThreshold() {
        ReflectionTestUtils.setField(service, "passThreshold", 91);
        ReflectionTestUtils.setField(service, "autoDetoxEnabled", false);
        ArticleState state = stateWith("我首先记录了这次测试结果。");

        ArticleQualityGateService.GateResult result = service.checkAndDetox(state, "t-threshold", USER_ID);

        assertEquals(90, result.score());
        assertFalse(result.passed());
        verify(skillRegistry, never()).createExecution(anyString(), anyMap());
    }

    // ──────────────────────────── 质量门禁用 ────────────────────────────

    @Test
    @DisplayName("质量门禁用 → 跳过检测，返回假通过")
    void checkAndDetox_disabled_skipped() {
        ReflectionTestUtils.setField(service, "enabled", false);
        ArticleState state = stateWith("在当今数字化快速发展的时代...");

        ArticleQualityGateService.GateResult result = service.checkAndDetox(state, "t5", USER_ID);

        assertTrue(result.passed());
        assertFalse(result.detoxed());
        verify(skillRegistry, never()).createExecution(anyString(), anyMap());
        verify(articleQualityMapper, never()).insert(any());
    }

    // ──────────────────────────── 空内容 ────────────────────────────

    @Test
    @DisplayName("内容为空 → 跳过，返回通过")
    void checkAndDetox_emptyContent_skipped() {
        ArticleState state = new ArticleState();
        state.setTaskId("t6");
        state.setContent("");
        state.setFullContent(null);

        ArticleQualityGateService.GateResult result = service.checkAndDetox(state, "t6", USER_ID);

        assertTrue(result.passed());
        assertFalse(result.detoxed());
        verify(skillRegistry, never()).createExecution(anyString(), anyMap());
        verify(articleQualityMapper, never()).insert(any());
    }

    // ──────────────────────────── VIP 判定 ────────────────────────────

    @Test
    @DisplayName("VIP 用户 → isVipOrAdmin 返回 true")
    void isVipOrAdmin_vipUser_returnsTrue() {
        User vip = new User();
        vip.setUserRole(UserConstant.VIP_ROLE);
        when(userService.getById(USER_ID)).thenReturn(vip);

        assertTrue(service.isVipOrAdmin(USER_ID));
    }

    @Test
    @DisplayName("管理员 → isVipOrAdmin 返回 true")
    void isVipOrAdmin_admin_returnsTrue() {
        User admin = new User();
        admin.setUserRole(UserConstant.ADMIN_ROLE);
        when(userService.getById(USER_ID)).thenReturn(admin);

        assertTrue(service.isVipOrAdmin(USER_ID));
    }

    @Test
    @DisplayName("普通用户 → isVipOrAdmin 返回 false")
    void isVipOrAdmin_normalUser_returnsFalse() {
        User normal = new User();
        normal.setUserRole("user");
        when(userService.getById(USER_ID)).thenReturn(normal);

        assertFalse(service.isVipOrAdmin(USER_ID));
    }

    @Test
    @DisplayName("用户不存在 → isVipOrAdmin 返回 false")
    void isVipOrAdmin_userNotFound_returnsFalse() {
        when(userService.getById(USER_ID)).thenReturn(null);

        assertFalse(service.isVipOrAdmin(USER_ID));
    }

    // ──────────────────────────── helpers ────────────────────────────

    private static ArticleState stateWith(String content) {
        ArticleState state = new ArticleState();
        state.setTaskId("t");
        state.setFullContent(content);
        return state;
    }
}
