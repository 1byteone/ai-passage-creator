package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.service.ExportService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ExportServiceImpl implements ExportService {

    private static final Pattern MD_H1 = Pattern.compile("^# (.+)$", Pattern.MULTILINE);
    private static final Pattern MD_H2 = Pattern.compile("^## (.+)$", Pattern.MULTILINE);
    private static final Pattern MD_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern MD_LIST = Pattern.compile("^- (.+)$", Pattern.MULTILINE);

    @Resource
    private ArticleMapper articleMapper;

    @Override
    public ByteArrayOutputStream export(String markdown, String title, Format format) {
        return switch (format) {
            case PDF -> exportPdf(markdown, title);
            case DOCX -> exportDocx(markdown, title);
            case PPTX -> exportPptx(markdown, title);
        };
    }

    @Override
    public byte[] exportArticle(String taskId, Format format) {
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new IllegalArgumentException("文章不存在: " + taskId);
        }
        String content = article.getContent();
        String title = article.getMainTitle() != null ? article.getMainTitle() : article.getTopic();
        return export(content != null ? content : "", title, format).toByteArray();
    }

    // ─── PDF ────────────────────────────────────────────

    private ByteArrayOutputStream exportPdf(String markdown, String title) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.lowagie.text.Document doc = null;
        try {
            doc = new com.lowagie.text.Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // 中文字体
            BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(bf, 20, Font.BOLD);
            Font h2Font = new Font(bf, 16, Font.BOLD);
            Font bodyFont = new Font(bf, 12, Font.NORMAL);

            // 标题
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            doc.add(titlePara);
            doc.add(Chunk.NEWLINE);

            // 逐行解析 Markdown
            for (String line : markdown.split("\n")) {
                if (line.startsWith("# ")) {
                    doc.add(new Paragraph(line.substring(2), h2Font));
                } else if (line.startsWith("## ")) {
                    doc.add(new Paragraph(line.substring(3), new Font(bf, 14, Font.BOLD)));
                } else if (line.startsWith("- ")) {
                    doc.add(new Paragraph("  • " + line.substring(2), bodyFont));
                } else if (!line.isBlank()) {
                    doc.add(new Paragraph(line, bodyFont));
                }
            }
        } catch (Exception e) {
            log.error("PDF 导出失败: {}", e.getMessage(), e);
            throw new RuntimeException("PDF 导出失败", e);
        } finally {
            // 确保 Document 始终关闭，防止资源泄漏
            if (doc != null && doc.isOpen()) {
                doc.close();
            }
        }
        return baos;
    }

    // ─── DOCX ────────────────────────────────────────────

    private ByteArrayOutputStream exportDocx(String markdown, String title) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            // 标题
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(20);

            // 正文
            for (String line : markdown.split("\n")) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                if (line.startsWith("# ")) {
                    run.setText(line.substring(2));
                    run.setBold(true);
                    run.setFontSize(16);
                } else if (line.startsWith("## ")) {
                    run.setText(line.substring(3));
                    run.setBold(true);
                    run.setFontSize(14);
                } else if (line.startsWith("- ")) {
                    run.setText("• " + line.substring(2));
                    run.setFontSize(12);
                } else if (!line.isBlank()) {
                    run.setText(line);
                    run.setFontSize(12);
                }
            }
            doc.write(baos);
        } catch (Exception e) {
            log.error("DOCX 导出失败: {}", e.getMessage(), e);
            throw new RuntimeException("DOCX 导出失败", e);
        }
        return baos;
    }

    // ─── PPTX ────────────────────────────────────────────

    private ByteArrayOutputStream exportPptx(String markdown, String title) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            // 标题页
            XSLFSlide titleSlide = ppt.createSlide();
            XSLFTextBox titleBox = titleSlide.createTextBox();
            titleBox.setAnchor(new java.awt.Rectangle(50, 200, 620, 100));
            XSLFTextRun tr = titleBox.addNewTextParagraph().addNewTextRun();
            tr.setText(title);
            tr.setBold(true);
            tr.setFontSize(36.0);
            tr.setFontColor(Color.BLACK);

            // 内容页 — 每 5 行一页
            var lines = Arrays.stream(markdown.split("\n"))
                    .filter(l -> !l.isBlank())
                    .toList();
            int slideCount = 0;
            for (int i = 0; i < lines.size(); i += 5) {
                XSLFSlide slide = ppt.createSlide();
                XSLFTextBox box = slide.createTextBox();
                box.setAnchor(new java.awt.Rectangle(40, 40, 640, 420));
                for (int j = i; j < Math.min(i + 5, lines.size()); j++) {
                    XSLFTextRun run = box.addNewTextParagraph().addNewTextRun();
                    run.setText(lines.get(j).replaceAll("^#+ ", ""));
                    run.setFontSize(18.0);
                }
                slideCount++;
            }

            ppt.write(baos);
        } catch (Exception e) {
            log.error("PPTX 导出失败: {}", e.getMessage(), e);
            throw new RuntimeException("PPTX 导出失败", e);
        }
        return baos;
    }
}
