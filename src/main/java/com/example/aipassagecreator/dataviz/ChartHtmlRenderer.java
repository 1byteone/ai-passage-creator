package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChartSpec → 服务端 SVG/HTML 渲染。无 JS、无 CDN，Playwright 禁 JS 也能截图。
 * 动态文本一律 escapeHtml，防 HTML 注入。
 */
@Component
public class ChartHtmlRenderer {

    private static final int CHART_W = 640;
    private static final int CHART_H = 320;
    private static final int PAD = 48;
    /** 饼图半径与类别上限（超出合并为「其他」，否则扇区无法辨识） */
    private static final double PIE_R = 100.0;
    private static final int MAX_PIE_CATS = 10;
    /** 散点半径 */
    private static final double DOT_R = 4.0;

    public String renderChart(ChartSpec spec, Dataset ds) {
        String body = switch (spec.chartType()) {
            case "bar" -> renderBar(spec, ds);
            case "line" -> renderLine(spec, ds);
            case "area" -> renderArea(spec, ds);
            case "pie" -> renderPie(spec, ds);
            case "scatter" -> renderScatter(spec, ds);
            case "table" -> renderTable(spec, ds);
            default -> throw new DatasetParseException("不支持的图表类型: " + spec.chartType());
        };
        StringBuilder sb = new StringBuilder();
        sb.append("<section class=\"chart-card ").append(styleClass(spec.style())).append("\">");
        sb.append("<h3>").append(escapeHtml(spec.title())).append("</h3>");
        if (spec.subtitle() != null && !spec.subtitle().isBlank()) {
            sb.append("<p class=\"subtitle\">").append(escapeHtml(spec.subtitle())).append("</p>");
        }
        sb.append(body);
        sb.append("<p class=\"meta\">");
        if (spec.unit() != null) sb.append("单位: ").append(escapeHtml(spec.unit())).append(" · ");
        sb.append("来源: ").append(escapeHtml(spec.source() == null ? "用户提供" : spec.source()));
        sb.append("</p></section>");
        return sb.toString();
    }

    /**
     * 整页报告。风格在报告级生效（字体/留白/边框），卡片级 accent 由 CSS 覆盖。
     * 只用系统字体，无外部字体、无 CDN、无脚本。
     */
    public String renderPage(String title, String style, List<String> chartFragments, List<String> warnings) {
        String safeStyle = switch (style == null ? "" : style) {
            case "mono", "editorial" -> style;
            default -> "glance";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
          .append("<title>").append(escapeHtml(title)).append("</title><style>")
          .append(BASE_CSS).append(STYLE_CSS)
          .append("</style></head><body data-style=\"").append(safeStyle).append("\">");
        sb.append("<main class=\"report\"><h1>").append(escapeHtml(title)).append("</h1>");
        if (warnings != null && !warnings.isEmpty()) {
            for (String w : warnings) {
                sb.append("<div class=\"warning\">").append(escapeHtml(w)).append("</div>");
            }
        }
        for (String f : chartFragments) sb.append(f);
        sb.append("</main></body></html>");
        return sb.toString();
    }

    /** 报告骨架样式（与风格无关的部分） */
    private static final String BASE_CSS =
            "body{margin:0;padding:32px 16px;color:#222;background:#fff;font-family:system-ui,-apple-system,'Segoe UI',sans-serif}"
          + ".report{margin:0 auto;max-width:820px}"
          + "h1{font-size:24px;margin:0 0 20px}"
          + ".chart-card{border:1px solid #ddd;border-radius:8px;padding:16px;margin:16px 0;background:#fff}"
          + ".chart-card h3{margin:0 0 4px;font-size:16px}"
          + ".subtitle{color:#666;margin:0 0 8px;font-size:13px}"
          + ".meta{color:#888;font-size:12px;margin:10px 0 0}"
          + "table{border-collapse:collapse;width:100%}"
          + "td,th{border:1px solid #ccc;padding:4px 8px;font-size:13px;text-align:left}"
          + ".grid-line{stroke:#e5e7eb;stroke-width:1}"
          + ".dot{fill:#2563eb;fill-opacity:.75}"
          + ".pie-slice{stroke:#fff;stroke-width:1}"
          + ".area-fill{fill:#2563eb;fill-opacity:.18;stroke:none}"
          + ".area-line{stroke:#2563eb;stroke-width:2}"
          + ".warning{background:#fff7ed;border:1px solid #fdba74;padding:8px 12px;border-radius:6px;font-size:13px;margin:10px 0}";

    /** 三套报告风格：mono 可打印 / glance 快速判断 / editorial 阅读式 */
    private static final String STYLE_CSS =
            "body[data-style=mono]{color:#111}"
          + "body[data-style=mono] .report{max-width:720px}"
          + "body[data-style=mono] .chart-card{border:1px solid #333;border-radius:2px}"
          + "body[data-style=mono] .bar-fill{fill:#333}"
          + "body[data-style=glance]{background:#f9fafb;color:#1f2937}"
          + "body[data-style=glance] .report{max-width:860px}"
          + "body[data-style=glance] .chart-card{border:1px solid #e5e7eb;border-radius:10px;box-shadow:0 1px 2px rgba(0,0,0,.05)}"
          + "body[data-style=glance] .bar-fill{fill:#2563eb}"
          + "body[data-style=editorial]{color:#111827;padding:48px 16px}"
          + "body[data-style=editorial] .report{max-width:680px;font-family:Georgia,'Times New Roman',serif}"
          + "body[data-style=editorial] h1{font-size:30px}"
          + "body[data-style=editorial] .chart-card{border:0;border-top:3px solid #111;border-radius:0;padding:20px 0;background:transparent}"
          + "body[data-style=editorial] .bar-fill{fill:#374151}";

    private String renderBar(ChartSpec spec, Dataset ds) {
        String x = spec.encoding().x(), y = spec.encoding().y();
        List<Map<String, String>> rows = new ArrayList<>(ds.rows());
        rows.sort((a, b) -> Double.compare(toD(b.get(y)), toD(a.get(y))));
        double max = rows.stream().mapToDouble(r -> toD(r.get(y))).max().orElse(1);
        if (max == 0) max = 1;
        int barH = 32;
        int innerH = rows.size() * (barH + 8);
        int innerW = CHART_W - PAD * 2;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox=\"0 0 ").append(CHART_W).append(' ').append(Math.max(innerH + PAD, CHART_H))
          .append("\" width=\"100%\">");
        for (int i = 0; i < rows.size(); i++) {
            double v = toD(rows.get(i).get(y));
            int yPos = PAD + i * (barH + 8);
            sb.append("<text x=\"").append(PAD).append("\" y=\"").append(yPos + barH / 2 + 4)
              .append("\" font-size=\"12\">").append(escapeHtml(rows.get(i).get(x))).append("</text>");
            sb.append("<rect class=\"bar-fill\" x=\"").append(PAD + 90).append("\" y=\"").append(yPos)
              .append("\" width=\"").append(Math.max(1, (int) (v / max * (innerW - 90))))
              .append("\" height=\"").append(barH).append("\" rx=\"3\"/>");
            sb.append("<text x=\"").append(CHART_W - PAD).append("\" y=\"").append(yPos + barH / 2 + 4)
              .append("\" font-size=\"12\" text-anchor=\"end\">").append(fmt(v)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private String renderLine(ChartSpec spec, Dataset ds) {
        String x = spec.encoding().x(), y = spec.encoding().y();
        List<Double> vals = ds.rows().stream().map(r -> toD(r.get(y))).toList();
        double min = vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = vals.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max == min) max = min + 1;
        int innerW = CHART_W - PAD * 2;
        int innerH = CHART_H - PAD * 2;
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < vals.size(); i++) {
            double px = PAD + (vals.size() == 1 ? innerW / 2.0 : innerW * i / (vals.size() - 1.0));
            double py = PAD + innerH - (vals.get(i) - min) / (max - min) * innerH;
            if (i > 0) pts.append(' ');
            pts.append(String.format("%.1f,%.1f", px, py));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox=\"0 0 ").append(CHART_W).append(' ').append(CHART_H)
          .append("\" width=\"100%\"><polyline fill=\"none\" stroke=\"#2563eb\" stroke-width=\"2\" points=\"")
          .append(pts).append("\"/>");
        appendXAxisLabels(sb, ds, x);
        sb.append("</svg>");
        return sb.toString();
    }

    /** 面积图：折线闭合到底部基线，再叠一条轮廓线以保持清晰 */
    private String renderArea(ChartSpec spec, Dataset ds) {
        List<Double> vals = ds.rows().stream().map(r -> toD(r.get(spec.encoding().y()))).toList();
        double min = vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = vals.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max == min) max = min + 1;
        int innerW = CHART_W - PAD * 2;
        int innerH = CHART_H - PAD * 2;
        double yBase = PAD + innerH;
        StringBuilder pts = new StringBuilder();
        double firstX = 0, lastX = 0;
        for (int i = 0; i < vals.size(); i++) {
            double px = PAD + (vals.size() == 1 ? innerW / 2.0 : innerW * i / (vals.size() - 1.0));
            double py = yBase - (vals.get(i) - min) / (max - min) * innerH;
            if (i == 0) firstX = px;
            lastX = px;
            if (i > 0) pts.append(' ');
            pts.append(String.format("%.1f,%.1f", px, py));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox=\"0 0 ").append(CHART_W).append(' ').append(CHART_H).append("\" width=\"100%\">");
        sb.append("<polygon class=\"area-fill\" points=\"").append(pts).append(' ')
          .append(String.format("%.1f,%.1f", lastX, yBase)).append(' ')
          .append(String.format("%.1f,%.1f", firstX, yBase)).append("\"/>");
        sb.append("<polyline class=\"area-line\" fill=\"none\" stroke-width=\"2\" points=\"")
          .append(pts).append("\"/>");
        appendXAxisLabels(sb, ds, spec.encoding().x());
        sb.append("</svg>");
        return sb.toString();
    }

    /** 饼图：按 (category, measure) 聚合，非正值过滤，超 10 类合并为「其他」 */
    private String renderPie(ChartSpec spec, Dataset ds) {
        String x = spec.encoding().x(), y = spec.encoding().y();
        // 同类别累加，保序
        Map<String, Double> sums = new LinkedHashMap<>();
        for (Map<String, String> row : ds.rows()) {
            double v = toD(row.get(y));
            if (v <= 0) continue;
            sums.merge(row.get(x) == null || row.get(x).isBlank() ? "未命名" : row.get(x), v, Double::sum);
        }
        List<Map.Entry<String, Double>> slices = new ArrayList<>(sums.entrySet());
        boolean merged = false;
        if (slices.size() > MAX_PIE_CATS) {
            slices.sort(Map.Entry.<String, Double>comparingByValue().reversed());
            double rest = slices.subList(MAX_PIE_CATS - 1, slices.size()).stream()
                    .mapToDouble(Map.Entry::getValue).sum();
            slices = new ArrayList<>(slices.subList(0, MAX_PIE_CATS - 1));
            slices.add(Map.entry("其他", rest));
            merged = true;
        }
        double total = slices.stream().mapToDouble(Map.Entry::getValue).sum();
        if (total <= 0) {
            return "<svg viewBox=\"0 0 " + CHART_W + " " + CHART_H + "\" width=\"100%\">"
                    + "<text x=\"" + (CHART_W / 2) + "\" y=\"" + (CHART_H / 2)
                    + "\" text-anchor=\"middle\" font-size=\"13\">无有效数值，无法绘制饼图</text></svg>";
        }
        double cx = CHART_W / 2.0, cy = CHART_H / 2.0;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox=\"0 0 ").append(CHART_W).append(' ').append(CHART_H).append("\" width=\"100%\">");
        double start = 0;
        for (Map.Entry<String, Double> e : slices) {
            double sweep = e.getValue() / total * 360;
            sb.append("<path d=\"M ").append(fmt(cx)).append(',').append(fmt(cy))
              .append(" L ").append(fmt(px(cx, start))).append(',').append(fmt(py(cy, start)))
              .append(" A ").append(fmt(PIE_R)).append(',').append(fmt(PIE_R)).append(" 0 ")
              .append(sweep > 180 ? 1 : 0).append(",1 ")
              .append(fmt(px(cx, start + sweep))).append(',').append(fmt(py(cy, start + sweep)))
              .append(" Z\" class=\"pie-slice\"/>");
            double mid = start + sweep / 2;
            double lx = cx + PIE_R * 0.62 * Math.cos(Math.toRadians(mid - 90));
            double ly = cy + PIE_R * 0.62 * Math.sin(Math.toRadians(mid - 90));
            sb.append("<text x=\"").append(fmt(lx)).append("\" y=\"").append(fmt(ly))
              .append("\" font-size=\"11\" text-anchor=\"middle\">")
              .append(escapeHtml(e.getKey())).append(' ').append(Math.round(sweep)).append("%</text>");
            start += sweep;
        }
        sb.append("</svg>");
        if (merged) {
            sb.append("<p class=\"meta\">类别超过 ").append(MAX_PIE_CATS)
              .append(" 项，已合并为「其他」</p>");
        }
        return sb.toString();
    }

    /** 极坐标 → SVG 直角坐标（0° 指向 12 点，顺时针） */
    private double px(double cx, double angle) {
        return cx + PIE_R * Math.cos(Math.toRadians(angle - 90));
    }

    private double py(double cy, double angle) {
        return cy + PIE_R * Math.sin(Math.toRadians(angle - 90));
    }

    /** 散点图：两个数值字段各自 min-max 归一后映射到绘图区 */
    private String renderScatter(ChartSpec spec, Dataset ds) {
        String x = spec.encoding().x(), y = spec.encoding().y();
        double minX = ds.rows().stream().mapToDouble(r -> toD(r.get(x))).min().orElse(0);
        double maxX = ds.rows().stream().mapToDouble(r -> toD(r.get(x))).max().orElse(1);
        double minY = ds.rows().stream().mapToDouble(r -> toD(r.get(y))).min().orElse(0);
        double maxY = ds.rows().stream().mapToDouble(r -> toD(r.get(y))).max().orElse(1);
        int innerW = CHART_W - PAD * 2;
        int innerH = CHART_H - PAD * 2;
        StringBuilder sb = new StringBuilder();
        // 三条参考线，帮助读数（不引入坐标轴库）
        sb.append("<svg viewBox=\"0 0 ").append(CHART_W).append(' ').append(CHART_H)
          .append("\" width=\"100%\">");
        for (int i = 0; i <= 2; i++) {
            double gy = PAD + innerH * i / 2.0;
            sb.append("<line x1=\"").append(PAD).append("\" y1=\"").append(fmt(gy))
              .append("\" x2=\"").append(CHART_W - PAD).append("\" y2=\"").append(fmt(gy))
              .append("\" class=\"grid-line\"/>");
        }
        for (Map<String, String> row : ds.rows()) {
            double nx = maxX == minX ? 0.5 : (toD(row.get(x)) - minX) / (maxX - minX);
            double ny = maxY == minY ? 0.5 : (toD(row.get(y)) - minY) / (maxY - minY);
            double cx = PAD + nx * innerW;
            double cy = PAD + innerH - ny * innerH;
            sb.append("<circle class=\"dot\" cx=\"").append(fmt(cx)).append("\" cy=\"").append(fmt(cy))
              .append("\" r=\"").append(fmt(DOT_R)).append("\"/>");
        }
        sb.append("<text x=\"").append(PAD).append("\" y=\"").append(CHART_H - 8)
          .append("\" font-size=\"11\">").append(escapeHtml(x)).append(' ').append(fmt(minX))
          .append(" → ").append(fmt(maxX)).append("</text>");
        sb.append("<text x=\"").append(CHART_W - PAD).append("\" y=\"").append(PAD - 8)
          .append("\" font-size=\"11\" text-anchor=\"end\">").append(escapeHtml(y)).append(' ')
          .append(fmt(maxY)).append("</text>");
        sb.append("</svg>");
        return sb.toString();
    }

    private void appendXAxisLabels(StringBuilder sb, Dataset ds, String x) {
        if (ds.rows().isEmpty()) return;
        sb.append("<text x=\"").append(PAD).append("\" y=\"").append(CHART_H - 8)
          .append("\" font-size=\"11\">").append(escapeHtml(firstRowValue(ds, x))).append("</text>");
        sb.append("<text x=\"").append(CHART_W - PAD).append("\" y=\"").append(CHART_H - 8)
          .append("\" font-size=\"11\" text-anchor=\"end\">")
          .append(escapeHtml(lastRowValue(ds, x))).append("</text>");
    }

    private String renderTable(ChartSpec spec, Dataset ds) {
        StringBuilder sb = new StringBuilder("<table><thead><tr>");
        for (String h : ds.headers()) sb.append("<th>").append(escapeHtml(h)).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (Map<String, String> row : ds.rows()) {
            sb.append("<tr>");
            for (String h : ds.headers()) sb.append("<td>").append(escapeHtml(row.get(h))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String firstRowValue(Dataset ds, String col) {
        return ds.rows().isEmpty() ? "" : ds.rows().get(0).get(col);
    }

    private String lastRowValue(Dataset ds, String col) {
        return ds.rows().isEmpty() ? "" : ds.rows().get(ds.rows().size() - 1).get(col);
    }

    private String styleClass(String style) {
        return switch (style) {
            case "glance" -> "glance";
            case "editorial" -> "editorial";
            default -> "mono";
        };
    }

    private double toD(String v) {
        try {
            return Double.parseDouble(v == null || v.isBlank() ? "0" : v.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}