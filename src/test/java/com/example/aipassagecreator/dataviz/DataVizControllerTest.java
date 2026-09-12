package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 报告产物查询端点：鉴权（未登录/越权/admin 放行）、404（记录与产物）、就绪状态、路径白名单。
 * <p>
 * 鉴权与产物访问全部 mock，只验证 Controller 的校验与派发；渲染与真实落盘不在本层覆盖。
 */
@ExtendWith(MockitoExtension.class)
class DataVizControllerTest {

    private static final String EXEC_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @Mock
    private DataVizStorageService storage;

    @Mock
    private SkillExecutionMapper executionMapper;

    @Mock
    private UserService userService;

    private DataVizController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new DataVizController(storage, executionMapper, userService);
        request = new MockHttpServletRequest();
    }

    @Test
    @DisplayName("未登录 → NOT_LOGIN_ERROR(40100)")
    void getArtifact_notLoggedIn_throwsNotLogin() {
        when(userService.getLoginUser(any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.getArtifact(EXEC_ID, request));

        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("执行记录不存在 → NOT_FOUND_ERROR(40400) 报告不存在")
    void getArtifact_executionMissing_throwsNotFound() {
        when(userService.getLoginUser(any())).thenReturn(user(OWNER_ID, "user"));
        when(executionMapper.selectOneByQuery(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.getArtifact(EXEC_ID, request));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), e.getCode());
        assertEquals("报告不存在", e.getMessage());
    }

    @Test
    @DisplayName("他人执行记录 → NO_AUTH_ERROR(40101) 无权访问")
    void getArtifact_otherUsersExecution_denied() {
        stubExecution(OTHER_ID);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.getArtifact(EXEC_ID, request));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
        assertEquals("无权访问该报告", e.getMessage());
    }

    @Test
    @DisplayName("PNG 端点同样做归属校验 → NO_AUTH_ERROR(40101)")
    void getPng_otherUsersExecution_denied() {
        stubExecution(OTHER_ID);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.getPng(EXEC_ID, request));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
    }

    @Test
    @DisplayName("admin → 放行他人执行记录")
    void getArtifact_admin_canReadOthers() {
        when(userService.getLoginUser(any())).thenReturn(user(OWNER_ID, "admin"));
        when(executionMapper.selectOneByQuery(any())).thenReturn(execution(OTHER_ID));

        var response = controller.getArtifact(EXEC_ID, request);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
    }

    @Test
    @DisplayName("本人执行记录 → 返回就绪状态与产物 URL")
    void getArtifact_owner_reportsReadiness() {
        stubExecution(OWNER_ID);
        when(storage.exists(any(), eq("report.html"))).thenReturn(true);
        when(storage.exists(any(), eq("report.png"))).thenReturn(false);

        DataVizArtifactVO vo = controller.getArtifact(EXEC_ID, request).getData();

        assertNotNull(vo);
        assertTrue(vo.htmlReady());
        assertFalse(vo.pngReady());
        assertEquals("/api/dataviz/" + EXEC_ID + "/html", vo.htmlUrl());
        assertEquals("/api/dataviz/" + EXEC_ID + "/png", vo.pngUrl());
    }

    @Test
    @DisplayName("HTML 未生成 → NOT_FOUND_ERROR 提示稍后刷新")
    void getHtml_missingArtifact_throwsNotFound() {
        stubExecution(OWNER_ID);
        when(storage.read(any(), eq("report.html"))).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.getHtml(EXEC_ID, request));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), e.getCode());
        assertEquals("报告尚未生成，请稍后刷新", e.getMessage());
    }

    @Test
    @DisplayName("HTML 已生成 → text/html 原样返回")
    void getHtml_ready_returnsHtml() {
        stubExecution(OWNER_ID);
        byte[] html = "<html>报告</html>".getBytes(StandardCharsets.UTF_8);
        when(storage.read(any(), eq("report.html"))).thenReturn(html);

        ResponseEntity<byte[]> response = controller.getHtml(EXEC_ID, request);

        assertEquals(MediaType.TEXT_HTML, response.getHeaders().getContentType());
        assertArrayEquals(html, response.getBody());
    }

    @Test
    @DisplayName("PNG 未导出 → NOT_FOUND_ERROR 提示渲染引擎不可用")
    void getPng_missingArtifact_throwsNotFound() {
        stubExecution(OWNER_ID);
        when(storage.read(any(), eq("report.png"))).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> controller.getPng(EXEC_ID, request));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), e.getCode());
        assertEquals("PNG 尚未导出或渲染引擎不可用", e.getMessage());
    }

    @Test
    @DisplayName("PNG 已导出 → image/png 原样返回")
    void getPng_ready_returnsPng() {
        stubExecution(OWNER_ID);
        byte[] png = {1, 2, 3};
        when(storage.read(any(), eq("report.png"))).thenReturn(png);

        ResponseEntity<byte[]> response = controller.getPng(EXEC_ID, request);

        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertArrayEquals(png, response.getBody());
    }

    @Test
    @DisplayName("非 UUID 标识 → 路径白名单拒绝，不触发任何查询")
    void nonUuidExecutionId_rejectedByWhitelist() {
        DataVizController real =
                new DataVizController(new DataVizStorageService(), executionMapper, userService);

        assertThrows(IllegalArgumentException.class, () -> real.getArtifact("../../etc", request));
        assertThrows(IllegalArgumentException.class, () -> real.getHtml("exec/../escape", request));
        assertThrows(IllegalArgumentException.class, () -> real.getPng("..", request));
    }

    private void stubExecution(Long ownerId) {
        when(userService.getLoginUser(any())).thenReturn(user(OWNER_ID, "user"));
        when(executionMapper.selectOneByQuery(any())).thenReturn(execution(ownerId));
    }

    private User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setUserRole(role);
        return u;
    }

    private SkillExecutionPo execution(Long userId) {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setSkillExecutionId(EXEC_ID);
        po.setUserId(userId);
        return po;
    }
}
