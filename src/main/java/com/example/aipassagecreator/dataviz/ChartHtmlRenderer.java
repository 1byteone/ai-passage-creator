package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    public String renderChart(ChartSpec spec, Dataset ds) {
        String body = switch (spec.chartType()) {
            case "bar" -> renderBar(spec, ds);
            case "line" -> renderLine(spec, ds);
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

    public String renderPage(String title, List<String> chartFragments, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
          .append("<title>").append(escapeHtml(title)).append("</title><style>")
          .append("body{font-family:sans-serif;color:#222;max-width:760px;margin:24px auto;padding:0 16px}")
          .append("h1{font-size:24px}.chart-card{border:1px solid #ddd;border-radius:8px;padding:16px;margin:16px 0}")
          .append(".subtitle{color:#666}.meta{color:#888;font-size:12px}")
          .append("table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:4px 8px;font-size:13px}")
          .append(".glance .bar-fill{fill:#2563eb}.mono .bar-fill{fill:#555}.editorial .bar-fill{fill:#374151}")
          .append(".warning{background:#fff7ed;border:1px solid #fdba74;padding:8px 12px;border-radius:6px;font-size:13px}")
          .append("</style></head><body>");
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>");
        if (warnings != null && !warnings.isEmpty()) {
            for (String w : warnings) {
                sb.append("<div class=\"warning\">").append(escapeHtml(w)).append("</div>");
            }
        }
        for (String f : chartFragments) sb.append(f);
        sb.append("</body></html>");
        return sb.toString();
    }

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
        if (!ds.rows().isEmpty()) {
            sb.append("<text x=\"").append(PAD).append("\" y=\"").append(CHART_H - 8)
              .append("\" font-size=\"11\">").append(escapeHtml(firstRowValue(ds, x))).append("</text>");
            sb.append("<text x=\"").append(CHART_W - PAD).append("\" y=\"").append(CHART_H - 8)
              .append("\" font-size=\"11\" text-anchor=\"end\">")
              .append(escapeHtml(lastRowValue(ds, x))).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
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