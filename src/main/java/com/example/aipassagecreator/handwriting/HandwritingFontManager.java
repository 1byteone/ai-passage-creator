package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingFont;
import com.example.aipassagecreator.service.CosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 手写字体管理器。
 * 字体文件存储在 COS handwriting/fonts/ 目录下，Playwright 通过预签名 URL 加载。
 * MVP 提供 3 款 SIL OFL 授权中文字体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandwritingFontManager {

    private final CosService cosService;

    private static final String COS_PREFIX = "handwriting/fonts";
    private static final Duration URL_DURATION = Duration.ofMinutes(30);

    private static final List<FontEntry> REGISTRY = List.of(
        new FontEntry("猫啃硬笔楷书", "maoken-yingbi", "maoken-yingbi.ttf", "春眠不觉晓，处处闻啼鸟"),
        new FontEntry("写意体", "xieyi", "xieyi.ttf", "夜来风雨声，花落知多少"),
        new FontEntry("随峰体Plus", "suifeng-plus", "suifeng-plus.ttf", "举头望明月，低头思故乡"),
        new FontEntry("Y式笔书体", "yshi-bishu", "yshi-bishu.ttf", "白日依山尽，黄河入海流"),
        new FontEntry("荷塘月色手写体", "hetang-yuese", "hetang-yuese.ttf", "欲穷千里目，更上一层楼"),
        new FontEntry("油茶馓子体", "youcha-sanzi", "youcha-sanzi.ttf", "床前明月光，疑是地上霜"),
        new FontEntry("白路棒棒手写体", "bailu-bangbang", "bailu-bangbang.ttf", "小时不识月，呼作白玉盘")
    );

    public List<HandwritingFont> listFonts() {
        return REGISTRY.stream()
                .map(e -> new HandwritingFont(e.name, e.key, e.previewText))
                .toList();
    }

    public String getFontUrl(String fontKey) {
        FontEntry entry = findByKey(fontKey);
        if (entry == null) {
            log.warn("字体不存在: key={}", fontKey);
            return null;
        }
        String cosKey = COS_PREFIX + "/" + entry.fileName;
        return cosService.generatePresignedUrl(cosKey, URL_DURATION);
    }

    public String generateFontFaceCss(String fontKey) {
        FontEntry entry = findByKey(fontKey);
        if (entry == null) {
            return "";
        }
        String url = getFontUrl(fontKey);
        if (url == null) {
            return "";
        }
        return "@font-face { font-family: '" + entry.name + "'; " +
               "src: url('" + url + "') format('truetype'); " +
               "font-display: block; }";
    }

    public String generateAllFontFacesCss(String primaryFontKey) {
        StringBuilder sb = new StringBuilder();
        for (FontEntry entry : REGISTRY) {
            String ff = generateFontFaceCss(entry.key);
            if (!ff.isEmpty()) {
                sb.append(ff).append("\n");
            }
        }
        FontEntry primary = findByKey(primaryFontKey);
        String primaryName = primary != null ? primary.name : "手书体";
        sb.append("body { font-family: '").append(primaryName)
          .append("', 'Noto Sans CJK SC', 'Microsoft YaHei', sans-serif; }\n");
        return sb.toString();
    }

    private FontEntry findByKey(String key) {
        return REGISTRY.stream()
                .filter(e -> e.key.equals(key))
                .findFirst().orElse(null);
    }

    private record FontEntry(String name, String key, String fileName, String previewText) {}
}
