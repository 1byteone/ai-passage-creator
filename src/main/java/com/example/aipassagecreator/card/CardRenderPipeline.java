package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PageResult;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Playwright 池化安全渲染管线。
 * <p>
 * 单例持有 Playwright Browser；每页渲染通过 {@link Semaphore}(3) 限制并发，
 * 禁用 JS、拒绝所有外部网络请求以保证安全，布局探针检测溢出后决定是否截图。
 * 无浏览器（未执行 playwright install chromium）时 {@link #init()} 优雅降级：
 * healthy=false，不抛启动异常。
 */
@Slf4j
@Component
public class CardRenderPipeline {

    private static final int RENDER_TIMEOUT_SECONDS = 15;
    private static final int BATCH_TIMEOUT_SECONDS = 60;
    private static final int MAX_PNG_BYTES = 2 * 1024 * 1024; // 2MB

    private Playwright playwright;
    private Browser browser;
    private final Semaphore renderPermits = new Semaphore(3);
    private boolean healthy = false;

    @Value("${playwright.headless:true}")
    private boolean headless;

    @PostConstruct
    public void init() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(headless));
            // 连通性自检
            try (var ctx = browser.newContext(); var page = ctx.newPage()) {
                page.setContent("<html><body>OK</body></html>");
            }
            healthy = true;
            log.info("Playwright 渲染引擎初始化完成，headless={}", headless);
        } catch (Exception e) {
            log.error("Playwright 初始化失败，卡片渲染不可用", e);
            healthy = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    public boolean isHealthy() { return healthy; }

    /**
     * 批量渲染 HTML 列表。每页：加载 → 布局探针 → 截图。
     * 布局不通过（溢出/裁切）→ 跳过截图，标记 fail。
     * 信号量由 {@link #renderOne} 的 finally 逐个 release，此处不触碰。
     */
    public List<PageResult> render(List<String> htmls, String taskId) {
        List<PageResult> results = new ArrayList<>();
        if (browser == null) {
            log.warn("渲染引擎不可用，跳过全部页面: taskId={}, pages={}", taskId, htmls.size());
            for (int i = 0; i < htmls.size(); i++) {
                results.add(PageResult.builder().pageNo(i + 1)
                        .errorMessage("渲染引擎不可用").build());
            }
            return results;
        }
        long batchStart = System.currentTimeMillis();
        for (int i = 0; i < htmls.size(); i++) {
            if (System.currentTimeMillis() - batchStart > BATCH_TIMEOUT_SECONDS * 1000) {
                log.warn("卡片渲染批次超时: taskId={}, done={}/{}", taskId, i, htmls.size());
                results.add(PageResult.builder().pageNo(i + 1)
                        .errorMessage("批次渲染超时").build());
                continue;
            }
            try {
                PageResult result = renderOne(htmls.get(i), i + 1);
                results.add(result);
            } catch (Exception e) {
                log.error("单页渲染失败: taskId={}, pageNo={}", taskId, i + 1, e);
                results.add(PageResult.builder().pageNo(i + 1)
                        .errorMessage(e.getMessage()).build());
            }
        }
        return results;
    }

    private PageResult renderOne(String html, int pageNo) throws Exception {
        if (!renderPermits.tryAcquire(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return PageResult.builder().pageNo(pageNo)
                    .errorMessage("渲染资源不足，超时等待").build();
        }
        long start = System.currentTimeMillis();
        try (var context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(1080, 1920)
                        .setDeviceScaleFactor(2.0)
                        .setJavaScriptEnabled(false));
             var page = context.newPage()) {

            // 单页操作（setContent/evaluate/screenshot）默认 15s 超时，
            // 避免挂起页面占用 permit 过长而超出批次 60s 上限
            page.setDefaultTimeout(RENDER_TIMEOUT_SECONDS * 1000);

            // 安全：拒绝所有外部网络请求
            page.route("**", route -> route.abort());

            // 加载 HTML
            page.setContent(html,
                    new Page.SetContentOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            // 布局探针：检查溢出（scrollHeight > clientHeight）
            Object result = page.evaluate(
                    "JSON.stringify({ scrollH: document.body.scrollHeight, " +
                    "clientH: window.innerHeight })");
            String json = result != null ? result.toString() : "{}";
            boolean layoutPassed = true;
            String layoutReport = "{}";
            try {
                int scrollH = Integer.parseInt(
                        json.replaceAll(".*\"scrollH\":(\\d+).*", "$1"));
                int clientH = Integer.parseInt(
                        json.replaceAll(".*\"clientH\":(\\d+).*", "$1"));
                layoutPassed = scrollH <= clientH + 5; // 5px 容差
                layoutReport = "{\"scrollH\":" + scrollH + ",\"clientH\":" + clientH + "}";
            } catch (Exception e) {
                log.warn("布局探针解析失败: pageNo={}, json={}", pageNo, json);
            }

            if (!layoutPassed) {
                return PageResult.builder().pageNo(pageNo)
                        .layoutPassed(false).layoutReport(layoutReport)
                        .errorMessage("页面内容溢出").build();
            }

            // 截图
            byte[] png = page.screenshot(
                    new Page.ScreenshotOptions()
                            .setType(ScreenshotType.PNG)
                            .setFullPage(false));

            int renderMs = (int) (System.currentTimeMillis() - start);
            if (png.length > MAX_PNG_BYTES) {
                log.warn("PNG 超限: pageNo={}, bytes={}", pageNo, png.length);
            }
            return PageResult.builder().pageNo(pageNo).pngBytes(png)
                    .layoutPassed(true).layoutReport(layoutReport)
                    .renderMs(renderMs).build();
        } finally {
            renderPermits.release();
        }
    }
}
