package com.example.aipassagecreator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 当前项目 Git 工作树的只读知识同步器。
 * 只读取固定的项目文档和 docs/knowledge，不执行 git 写操作，不读取日志、密钥或任意用户路径。
 */
@Slf4j
@Service
public class RagKnowledgeSyncService {

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+?)\\s*$");
    private static final Set<String> ROOT_DOCUMENTS = Set.of(
            "AGENTS.md", "CLAUDE.md", "PROJECT_ARCHITECTURE.md", "CONTRIBUTING.md", "DEPLOYMENT.md", "README.md");
    private static final Set<String> EXTENSIONS = Set.of(".md", ".markdown");

    private final RagDocumentStore documentStore;
    private final Path projectRoot;
    private final String configuredBranch;

    public RagKnowledgeSyncService(RagDocumentStore documentStore,
                                   @Value("${rag.knowledge.source-root:.}") String sourceRoot,
                                   @Value("${rag.knowledge.branch:dev_rag}") String configuredBranch) {
        this.documentStore = documentStore;
        this.projectRoot = Paths.get(sourceRoot).toAbsolutePath().normalize();
        this.configuredBranch = configuredBranch;
    }

    public record SyncResult(int files, int sections, String branch, String commitSha, List<String> sources) {
    }

    public SyncResult sync() {
        List<Path> files = discoverFiles();
        String branch = gitValue("branch --show-current", configuredBranch);
        String commit = gitValue("rev-parse HEAD", "working-tree");
        int sections = 0;
        List<String> sources = new ArrayList<>();
        for (Path file : files) {
            String relative = projectRoot.relativize(file).toString().replace('\\', '/');
            String prefix = "git:" + relative + "#";
            documentStore.deleteBySourcePrefix(prefix);
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("读取研发知识文档失败: path={}, err={}", relative, e.getMessage());
                continue;
            }
            List<Section> parsed = parseSections(text);
            String title = titleOf(relative, text);
            String domain = domainOf(relative);
            String kind = kindOf(domain);
            String checksum = sha256(text);
            for (int i = 0; i < parsed.size(); i++) {
                Section section = parsed.get(i);
                String source = prefix + i;
                documentStore.upsertGit(title, source, section.text(), domain, kind, branch, commit,
                        relative, section.path(), checksum);
                sections++;
                sources.add(source);
            }
        }
        return new SyncResult(files.size(), sections, branch, commit, List.copyOf(sources));
    }

    private List<Path> discoverFiles() {
        Set<Path> paths = new LinkedHashSet<>();
        for (String name : ROOT_DOCUMENTS) {
            Path path = projectRoot.resolve(name).normalize();
            if (isInsideRoot(path) && Files.isRegularFile(path)) {
                paths.add(path);
            }
        }
        Path knowledge = projectRoot.resolve("docs/knowledge").normalize();
        if (isInsideRoot(knowledge) && Files.isDirectory(knowledge)) {
            try (var stream = Files.walk(knowledge)) {
                stream.filter(Files::isRegularFile)
                        .filter(this::isMarkdown)
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(paths::add);
            } catch (IOException e) {
                log.warn("扫描研发知识目录失败: path={}, err={}", knowledge, e.getMessage());
            }
        }
        return List.copyOf(paths);
    }

    private boolean isInsideRoot(Path path) {
        return path.startsWith(projectRoot);
    }

    private boolean isMarkdown(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private List<Section> parseSections(String text) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADING.matcher(text);
        List<Heading> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new Heading(matcher.start(), matcher.end(), matcher.group(1).length(), matcher.group(2).trim()));
        }
        if (headings.isEmpty()) {
            return text.isBlank() ? List.of() : List.of(new Section("文档", text.trim()));
        }
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            int end = i + 1 < headings.size() ? headings.get(i + 1).start() : text.length();
            String sectionText = text.substring(heading.start(), end).trim();
            if (!sectionText.isBlank()) {
                sections.add(new Section(heading.title(), sectionText));
            }
        }
        return sections;
    }

    private String titleOf(String relative, String text) {
        Matcher matcher = HEADING.matcher(text);
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        Path path = Paths.get(relative);
        String name = path.getFileName().toString();
        return name.replaceFirst("\\.(?i:md|markdown)$", "");
    }

    private String domainOf(String relative) {
        String normalized = relative.toLowerCase(Locale.ROOT);
        if (normalized.contains("/testing/") || normalized.contains("test")) return "testing";
        if (normalized.contains("/development/") || normalized.contains("plan")) return "development";
        if (normalized.contains("/decisions/") || normalized.contains("adr")) return "decision";
        if (normalized.contains("/business/") || normalized.contains("workflow")) return "business";
        return "standard";
    }

    private String kindOf(String domain) {
        return switch (domain) {
            case "testing" -> "test-strategy";
            case "development" -> "implementation-plan";
            case "decision" -> "adr";
            case "business" -> "workflow";
            default -> "project-rule";
        };
    }

    private String gitValue(String arguments, String fallback) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(arguments.split(" ")));
            Process process = new ProcessBuilder(command)
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return fallback;
            }
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && !value.isBlank() ? value : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Heading(int start, int end, int level, String title) {
    }

    private record Section(String path, String text) {
    }
}
