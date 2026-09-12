package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingParams;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 手写效果核心渲染器。
 * 职责：文本 + 参数 → 含手写效果 + 字体 + 纸张的完整自包含 HTML 页面。
 *
 * <p>安全：用户内容在注入 HTML 前经过 Jsoup.clean() 清洗，
 * 清除 script/style/iframe 标签，只保留基本格式化标签。
 * 不通过 Thymeleaf（避免 th:utext 安全顾虑）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandwritingRenderer {

    private final HandwritingFontManager fontManager;
    private final HandwritingPaperService paperService;

    /**
     * 将手写请求渲染为完整自包含 HTML 页面。
     */
    public String renderToHtml(HandwritingRequest request) {
        String sanitized = sanitizeContent(request.content());
        var charReport = analyzeCharacterCoverage(sanitized);
        String fontCss = fontManager.generateAllFontFacesCss(request.fontName());
        String paperCss = paperService.generatePaperCss(
                request.paperType(), request.paperImageUrl());
        return buildHtml(sanitized, fontCss, paperCss,
                request.effectiveParams(), charReport.hasWarning());
    }

    /**
     * HTML 清洗：Jsoup 清除 script/style/iframe，保留基本格式化标签。
     */
    String sanitizeContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }
        Safelist safelist = Safelist.basic()
                .addTags("h1", "h2", "h3", "h4", "h5", "h6",
                         "pre", "code", "blockquote", "hr")
                .addAttributes("pre", "class")
                .addAttributes("code", "class");
        // 用白名单内的 br 保留用户粘贴文本中的换行，避免 Jsoup 清洗时折叠为空格。
        String withBreaks = rawContent.replace("\r\n", "\n").replace("\r", "\n")
                .replace("\n", "<br>");
        return Jsoup.clean(withBreaks, safelist);
    }

    /**
     * 生僻字覆盖率分析。
     */
    CharacterCoverageReport analyzeCharacterCoverage(String text) {
        if (text == null || text.isBlank()) {
            return new CharacterCoverageReport(100.0, false);
        }
        int totalCjk = 0;
        int unusual = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                totalCjk++;
                if (c > 0x9FA5) {
                    unusual++;
                }
            }
        }
        if (totalCjk == 0) {
            return new CharacterCoverageReport(100.0, false);
        }
        double coverage = 100.0 * (totalCjk - unusual) / totalCjk;
        boolean hasWarning = coverage < 95.0;
        if (hasWarning) {
            log.warn("文本含较多生僻字，手写字体可能无法覆盖: coverage={:.1f}%", coverage);
        }
        return new CharacterCoverageReport(coverage, hasWarning);
    }

    public record CharacterCoverageReport(double coveragePercent, boolean hasWarning) {}

    private String buildHtml(String content, String fontCss, String paperCss,
                             HandwritingParams p, boolean hasCharWarning) {
        String warningHtml = hasCharWarning
                ? "<div class=\"char-warning\"><span>部分字符可能无法用手写字体显示</span></div>"
                : "";

        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: 1240px; min-height: 1754px;
    %s
    padding: 60px 80px;
    font-size: 28px;
    line-height: 2.0;
    color: #2D1810;
    -webkit-font-smoothing: antialiased;
  }
  %s
  .content {
    max-width: 100%%;
    word-wrap: break-word;
    overflow-wrap: anywhere;
    white-space: pre-wrap;
  }
  .content p { margin-bottom: 8px; }
  .content h1, .content h2, .content h3 {
    margin-top: 20px; margin-bottom: 12px;
  }
  .char-warning {
    position: fixed; top: 10px; right: 10px;
    font-size: 12px; color: #999;
  }
  .char-warning span { background: #FFF3CD; padding: 2px 6px; border-radius: 4px; }
</style>
</head>
<body>
<div class="content">%s</div>
%s
<script>
(function() {
  var params = { posJitter: %.1f, rotJitter: %.1f, sizeJitter: %.1f, ink: %.2f };
  function applyJitter(el) {
    if (!el || !el.childNodes) return;
    el.childNodes.forEach(function(node) {
      if (node.nodeType === 3 && node.textContent.trim()) {
        var frag = document.createDocumentFragment();
        node.textContent.split('').forEach(function(ch) {
          var span = document.createElement('span');
          span.textContent = ch;
          span.style.display = 'inline-block';
          span.style.position = 'relative';
          var dx = (Math.random() - 0.5) * params.posJitter * 2;
          var dy = (Math.random() - 0.5) * params.posJitter;
          span.style.left = dx + 'px';
          span.style.top = dy + 'px';
          var rot = (Math.random() - 0.5) * params.rotJitter * 2;
          span.style.transform = 'rotate(' + rot + 'deg)';
          var s = 1 + (Math.random() - 0.5) * params.sizeJitter / 50.0;
          span.style.fontSize = (100 * s).toFixed(0) + '%%';
          var alpha = params.ink + (Math.random() - 0.5) * 0.1;
          span.style.opacity = Math.max(0, Math.min(1, alpha)).toFixed(2);
          frag.appendChild(span);
        });
        node.parentNode.replaceChild(frag, node);
      } else if (node.nodeType === 1) {
        applyJitter(node);
      }
    });
  }
  applyJitter(document.querySelector('.content'));
})();
</script>
</body>
</html>
""".formatted(
                paperCss, fontCss, content, warningHtml,
                p.positionJitter(), p.rotationJitter(),
                p.sizeJitter(), p.inkDensity());
    }
}
