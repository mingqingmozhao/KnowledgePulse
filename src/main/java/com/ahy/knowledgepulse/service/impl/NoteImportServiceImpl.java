package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.ImportResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteFolder;
import com.ahy.knowledgepulse.entity.NoteTag;
import com.ahy.knowledgepulse.entity.NoteVersion;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteFolderMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteTagMapper;
import com.ahy.knowledgepulse.mapper.NoteVersionMapper;
import com.ahy.knowledgepulse.service.NoteImportService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NoteImportServiceImpl implements NoteImportService {

    private static final int MAX_IMPORT_FILES = 500;
    private static final long MAX_IMPORT_BYTES = 50L * 1024 * 1024;
    private static final int MAX_WARNINGS = 12;
    private static final int MAX_TAGS_PER_NOTE = 30;
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("\\A---\\R([\\s\\S]*?)\\R---\\R?");
    private static final Pattern H1_PATTERN = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final Pattern HASH_TAG_PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}_/-])#([\\p{L}\\p{N}_/-]+)");
    private static final Set<String> SUPPORTED_MODES = Set.of("MARKDOWN_FOLDER", "OBSIDIAN_VAULT", "BATCH_MARKDOWN");
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".obsidian", ".trash", ".git", "node_modules");

    private final NoteMapper noteMapper;
    private final NoteFolderMapper folderMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteVersionMapper versionMapper;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public ImportResponse importMarkdownFiles(
            List<MultipartFile> files,
            List<String> paths,
            String mode,
            String rootFolderName,
            Long targetFolderId
    ) {
        Long userId = requireCurrentUser();
        String normalizedMode = normalizeMode(mode);
        validateTargetFolder(targetFolderId, userId);

        List<UploadItem> uploadItems = buildUploadItems(files, paths);
        ImportResponse response = new ImportResponse();
        response.setMode(normalizedMode);
        response.setTotalFiles(uploadItems.size());

        List<UploadItem> markdownItems = uploadItems.stream()
                .sorted(Comparator.comparing(UploadItem::path, String.CASE_INSENSITIVE_ORDER))
                .filter(item -> isImportableMarkdown(item, response))
                .toList();

        if (markdownItems.isEmpty()) {
            throw new BusinessException(400, "没有可导入的 Markdown 文件");
        }

        String commonTopDirectory = detectCommonTopDirectory(markdownItems).orElse(null);
        String resolvedRootName = resolveRootFolderName(rootFolderName, commonTopDirectory, normalizedMode);
        NoteFolder rootFolder = createFolder(userId, uniqueFolderName(userId, targetFolderId, resolvedRootName), targetFolderId);
        AtomicInteger createdFolderCount = new AtomicInteger(1);
        Map<String, Long> folderCache = new HashMap<>();
        folderCache.put("", rootFolder.getId());
        Set<String> allTags = new LinkedHashSet<>();

        response.setRootFolderId(rootFolder.getId());
        response.setRootFolderName(rootFolder.getName());

        for (UploadItem item : markdownItems) {
            try {
                MarkdownDocument document = parseMarkdown(readUtf8(item.file()));
                List<String> noteTags = normalizeTags(document.tags());
                String content = document.content();
                String title = resolveTitle(content, item.path());
                Long folderId = resolveFolderId(
                        userId,
                        rootFolder.getId(),
                        folderCache,
                        folderSegments(item.path(), commonTopDirectory),
                        createdFolderCount
                );

                Note note = new Note();
                note.setUserId(userId);
                note.setFolderId(folderId);
                note.setTitle(title);
                note.setContent(content);
                note.setHtmlContent(defaultHtml(content));
                note.setTags(String.join(",", noteTags));
                note.setIsPublic(0);
                note.setDeleted(0);
                noteMapper.insert(note);

                persistTags(note.getId(), noteTags);
                saveInitialVersion(note);
                allTags.addAll(noteTags);
                response.getNotes().add(importedNoteItem(note, item.path(), noteTags));
            } catch (IOException ex) {
                response.setSkippedFiles(response.getSkippedFiles() + 1);
                addWarning(response, "读取失败：" + item.path());
            }
        }

        response.setImportedNotes(response.getNotes().size());
        response.setCreatedFolders(createdFolderCount.get());
        response.setTags(new ArrayList<>(allTags));
        operationLogService.record(userId, "IMPORT", "Imported " + response.getImportedNotes() + " markdown notes");
        return response;
    }

    private Long requireCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return userId;
    }

    private String normalizeMode(String mode) {
        String normalized = StringUtils.hasText(mode)
                ? mode.trim().toUpperCase(Locale.ROOT)
                : "MARKDOWN_FOLDER";
        if (!SUPPORTED_MODES.contains(normalized)) {
            throw new BusinessException(400, "不支持的导入模式");
        }
        return normalized;
    }

    private void validateTargetFolder(Long targetFolderId, Long userId) {
        if (targetFolderId == null) {
            return;
        }

        NoteFolder folder = folderMapper.selectById(targetFolderId);
        if (folder == null) {
            throw new BusinessException(400, "目标文件夹不存在");
        }
        if (!Objects.equals(folder.getUserId(), userId)) {
            throw new BusinessException(403, "不能导入到其他用户的文件夹");
        }
    }

    private List<UploadItem> buildUploadItems(List<MultipartFile> files, List<String> paths) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(400, "请选择要导入的 Markdown 文件");
        }
        if (files.size() > MAX_IMPORT_FILES) {
            throw new BusinessException(400, "一次最多导入 " + MAX_IMPORT_FILES + " 个文件");
        }

        long totalBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_IMPORT_BYTES) {
            throw new BusinessException(400, "单次导入总大小不能超过 50MB");
        }

        List<UploadItem> items = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String submittedPath = paths != null && index < paths.size() ? paths.get(index) : null;
            String normalizedPath = normalizePath(StringUtils.hasText(submittedPath) ? submittedPath : file.getOriginalFilename());
            if (!StringUtils.hasText(normalizedPath)) {
                normalizedPath = "untitled-" + (index + 1) + ".md";
            }
            items.add(new UploadItem(file, normalizedPath));
        }
        return items;
    }

    private boolean isImportableMarkdown(UploadItem item, ImportResponse response) {
        if (isIgnoredPath(item.path()) || !isMarkdownFile(item.path())) {
            response.setSkippedFiles(response.getSkippedFiles() + 1);
            return false;
        }
        return true;
    }

    private boolean isMarkdownFile(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".md") || normalized.endsWith(".markdown");
    }

    private boolean isIgnoredPath(String path) {
        return splitPath(path).stream()
                .map(segment -> segment.toLowerCase(Locale.ROOT))
                .anyMatch(IGNORED_DIRECTORIES::contains);
    }

    private Optional<String> detectCommonTopDirectory(List<UploadItem> items) {
        Set<String> topDirectories = new HashSet<>();
        for (UploadItem item : items) {
            List<String> segments = splitPath(item.path());
            if (segments.size() < 2) {
                return Optional.empty();
            }
            topDirectories.add(segments.get(0));
        }
        return topDirectories.size() == 1 ? Optional.of(topDirectories.iterator().next()) : Optional.empty();
    }

    private String resolveRootFolderName(String rootFolderName, String commonTopDirectory, String mode) {
        if (StringUtils.hasText(rootFolderName)) {
            return sanitizeName(rootFolderName, fallbackRootFolderName(mode), 80);
        }
        if (StringUtils.hasText(commonTopDirectory)) {
            return sanitizeName(commonTopDirectory, fallbackRootFolderName(mode), 80);
        }
        return fallbackRootFolderName(mode);
    }

    private String fallbackRootFolderName(String mode) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HHmm"));
        return switch (mode) {
            case "OBSIDIAN_VAULT" -> "Obsidian 导入 " + timestamp;
            case "BATCH_MARKDOWN" -> "批量 Markdown 导入 " + timestamp;
            default -> "Markdown 导入 " + timestamp;
        };
    }

    private String uniqueFolderName(Long userId, Long parentId, String baseName) {
        String normalizedBaseName = sanitizeName(baseName, "导入", 80);
        String candidate = normalizedBaseName;
        int suffix = 2;
        while (folderNameExists(userId, parentId, candidate)) {
            candidate = trimToLength(normalizedBaseName, 72) + " (" + suffix + ")";
            suffix++;
        }
        return candidate;
    }

    private boolean folderNameExists(Long userId, Long parentId, String name) {
        LambdaQueryWrapper<NoteFolder> queryWrapper = new LambdaQueryWrapper<NoteFolder>()
                .eq(NoteFolder::getUserId, userId)
                .eq(NoteFolder::getName, name);
        if (parentId == null) {
            queryWrapper.isNull(NoteFolder::getParentId);
        } else {
            queryWrapper.eq(NoteFolder::getParentId, parentId);
        }
        return folderMapper.selectCount(queryWrapper) > 0;
    }

    private NoteFolder createFolder(Long userId, String name, Long parentId) {
        NoteFolder folder = new NoteFolder();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setParentId(parentId);
        folderMapper.insert(folder);
        return folder;
    }

    private Long resolveFolderId(
            Long userId,
            Long rootFolderId,
            Map<String, Long> folderCache,
            List<String> segments,
            AtomicInteger createdFolderCount
    ) {
        Long parentId = rootFolderId;
        String cachePath = "";

        for (String segment : segments) {
            String folderName = sanitizeName(segment, "未命名文件夹", 80);
            cachePath = cachePath + "/" + folderName.toLowerCase(Locale.ROOT);
            Long cachedId = folderCache.get(cachePath);
            if (cachedId != null) {
                parentId = cachedId;
                continue;
            }

            NoteFolder folder = createFolder(userId, folderName, parentId);
            folderCache.put(cachePath, folder.getId());
            parentId = folder.getId();
            createdFolderCount.incrementAndGet();
        }

        return parentId;
    }

    private MarkdownDocument parseMarkdown(String rawContent) {
        String content = stripBom(rawContent);
        List<String> tags = new ArrayList<>();
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            tags.addAll(parseFrontMatterTags(matcher.group(1)));
            content = content.substring(matcher.end());
        }
        tags.addAll(parseHashTags(content));
        return new MarkdownDocument(content, tags);
    }

    private List<String> parseFrontMatterTags(String frontMatter) {
        List<String> tags = new ArrayList<>();
        String[] lines = frontMatter.split("\\R");

        for (int index = 0; index < lines.length; index++) {
            String trimmed = lines[index].trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("tags:") && !lower.startsWith("tag:")) {
                continue;
            }

            String tail = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            if (StringUtils.hasText(tail)) {
                tags.addAll(parseTagValue(tail));
                continue;
            }

            for (int childIndex = index + 1; childIndex < lines.length; childIndex++) {
                String childLine = lines[childIndex];
                String childTrimmed = childLine.trim();
                if (!StringUtils.hasText(childTrimmed)) {
                    continue;
                }
                if (!Character.isWhitespace(childLine.charAt(0)) && !childTrimmed.startsWith("-")) {
                    break;
                }
                if (childTrimmed.startsWith("-")) {
                    tags.addAll(parseTagValue(childTrimmed.substring(1).trim()));
                }
            }
        }

        return tags;
    }

    private List<String> parseTagValue(String value) {
        String cleaned = stripWrappingQuotes(value.trim());
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }

        String[] parts = cleaned.contains(",") || cleaned.contains("，")
                ? cleaned.split("[,，]")
                : new String[]{cleaned};
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            tags.add(stripWrappingQuotes(part.trim()));
        }
        return tags;
    }

    private List<String> parseHashTags(String content) {
        List<String> tags = new ArrayList<>();
        Matcher matcher = HASH_TAG_PATTERN.matcher(content);
        while (matcher.find()) {
            tags.add(matcher.group(1));
        }
        return tags;
    }

    private List<String> normalizeTags(Collection<String> rawTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (rawTags == null) {
            return List.of();
        }

        for (String rawTag : rawTags) {
            String tag = normalizeTag(rawTag);
            if (StringUtils.hasText(tag)) {
                tags.add(tag);
            }
            if (tags.size() >= MAX_TAGS_PER_NOTE) {
                break;
            }
        }

        return new ArrayList<>(tags);
    }

    private String normalizeTag(String rawTag) {
        if (!StringUtils.hasText(rawTag)) {
            return "";
        }
        String tag = stripWrappingQuotes(rawTag.trim())
                .replace("\\", "/")
                .replaceAll("^#+", "")
                .trim();
        if (!StringUtils.hasText(tag)) {
            return "";
        }
        return trimToLength(tag, 50);
    }

    private String resolveTitle(String content, String path) {
        Matcher matcher = H1_PATTERN.matcher(content);
        if (matcher.find()) {
            return sanitizeTitle(matcher.group(1), fileBaseName(path));
        }
        return sanitizeTitle(fileBaseName(path), "未命名导入笔记");
    }

    private String sanitizeTitle(String value, String fallback) {
        String stripped = stripWrappingQuotes(value == null ? "" : value)
                .replace("`", "")
                .replace("[[", "")
                .replace("]]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitizeName(stripped, fallback, 180);
    }

    private String fileBaseName(String path) {
        List<String> segments = splitPath(path);
        String fileName = segments.isEmpty() ? path : segments.get(segments.size() - 1);
        return fileName.replaceFirst("(?i)\\.(md|markdown)$", "");
    }

    private void persistTags(Long noteId, List<String> tags) {
        for (String tag : tags) {
            NoteTag noteTag = new NoteTag();
            noteTag.setNoteId(noteId);
            noteTag.setTagName(tag);
            noteTagMapper.insert(noteTag);
        }
    }

    private void saveInitialVersion(Note note) {
        NoteVersion version = new NoteVersion();
        version.setNoteId(note.getId());
        version.setVersion(1);
        version.setContentSnapshot(note.getContent());
        versionMapper.insert(version);
    }

    private ImportResponse.ImportedNoteItem importedNoteItem(Note note, String path, List<String> tags) {
        ImportResponse.ImportedNoteItem item = new ImportResponse.ImportedNoteItem();
        item.setId(note.getId());
        item.setTitle(note.getTitle());
        item.setPath(path);
        item.setFolderId(note.getFolderId());
        item.setTags(tags);
        return item;
    }

    private List<String> folderSegments(String path, String commonTopDirectory) {
        List<String> segments = new ArrayList<>(splitPath(path));
        if (segments.isEmpty()) {
            return List.of();
        }
        segments.remove(segments.size() - 1);
        if (StringUtils.hasText(commonTopDirectory)
                && !segments.isEmpty()
                && segments.get(0).equalsIgnoreCase(commonTopDirectory)) {
            segments.remove(0);
        }
        return segments;
    }

    private List<String> splitPath(String path) {
        if (!StringUtils.hasText(path)) {
            return List.of();
        }
        String normalized = path.replace("\\", "/");
        String[] rawSegments = normalized.split("/");
        List<String> segments = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            String segment = rawSegment.trim();
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                continue;
            }
            segments.add(segment);
        }
        return segments;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return String.join("/", splitPath(path.replace("\u0000", "")));
    }

    private String readUtf8(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private String stripBom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value == null ? "" : value;
    }

    private String stripWrappingQuotes(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String sanitizeName(String value, String fallback, int maxLength) {
        String normalized = value == null ? "" : value
                .replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001F]", "-")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            normalized = fallback;
        }
        return trimToLength(normalized, maxLength);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private String defaultHtml(String content) {
        String escapedContent = content == null ? "" : content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<pre>" + escapedContent + "</pre>";
    }

    private void addWarning(ImportResponse response, String warning) {
        if (response.getWarnings().size() < MAX_WARNINGS) {
            response.getWarnings().add(warning);
        }
    }

    private record UploadItem(MultipartFile file, String path) {
    }

    private record MarkdownDocument(String content, List<String> tags) {
    }
}
