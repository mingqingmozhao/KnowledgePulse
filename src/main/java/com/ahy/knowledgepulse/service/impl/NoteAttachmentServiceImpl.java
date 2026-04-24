package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.AttachmentResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteAttachment;
import com.ahy.knowledgepulse.entity.NoteAttachmentReference;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteAttachmentMapper;
import com.ahy.knowledgepulse.mapper.NoteAttachmentReferenceMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.service.NoteAttachmentService;
import com.ahy.knowledgepulse.service.NotePermissionService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteAttachmentServiceImpl implements NoteAttachmentService {

    private static final long MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L;
    private static final String PUBLIC_ATTACHMENT_PREFIX = "/api/v1/media/attachments/";
    private static final Pattern ATTACHMENT_ID_PATTERN = Pattern.compile("(?:attachmentId=|data-attachment-id=[\"']?)(\\d+)");

    private static final Map<String, String> TYPE_BY_EXTENSION = Map.ofEntries(
            Map.entry("png", "IMAGE"),
            Map.entry("jpg", "IMAGE"),
            Map.entry("jpeg", "IMAGE"),
            Map.entry("webp", "IMAGE"),
            Map.entry("gif", "IMAGE"),
            Map.entry("pdf", "PDF"),
            Map.entry("doc", "WORD"),
            Map.entry("docx", "WORD")
    );

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry("image/png", "png"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
    );

    private final NoteAttachmentMapper attachmentMapper;
    private final NoteAttachmentReferenceMapper referenceMapper;
    private final NoteMapper noteMapper;
    private final NotePermissionService notePermissionService;
    private final OperationLogService operationLogService;

    @Value("${knowledgepulse.attachment-storage-dir:storage/attachments}")
    private String attachmentStorageDir;

    @Override
    @Transactional
    public AttachmentResponse upload(MultipartFile file) {
        Long userId = requireCurrentUser();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "Attachment file is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BusinessException(400, "Attachment must be 25MB or smaller");
        }

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String extension = resolveExtension(originalName, contentType);
        String fileType = TYPE_BY_EXTENSION.get(extension);
        if (fileType == null) {
            throw new BusinessException(400, "Only images, PDF, Word .doc or .docx files are supported");
        }

        Path userStorageDirectory = resolveAttachmentStorageDirectory().resolve(String.valueOf(userId)).normalize();
        Path rootDirectory = resolveAttachmentStorageDirectory();
        if (!userStorageDirectory.startsWith(rootDirectory)) {
            throw new BusinessException(400, "Invalid attachment path");
        }

        String storedName = "attachment-" + UUID.randomUUID() + "." + extension;
        Path targetFile = userStorageDirectory.resolve(storedName).normalize();
        if (!targetFile.startsWith(userStorageDirectory)) {
            throw new BusinessException(400, "Invalid attachment file path");
        }

        try {
            Files.createDirectories(userStorageDirectory);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(500, "Failed to save attachment: " + ex.getMessage());
        }

        NoteAttachment attachment = new NoteAttachment();
        attachment.setUserId(userId);
        attachment.setOriginalName(originalName);
        attachment.setStoredName(storedName);
        attachment.setStoragePath(userId + "/" + storedName);
        attachment.setFileUrl(PUBLIC_ATTACHMENT_PREFIX + userId + "/" + storedName);
        attachment.setContentType(resolvePublicContentType(contentType, extension));
        attachment.setFileType(fileType);
        attachment.setFileSize(file.getSize());
        attachmentMapper.insert(attachment);

        operationLogService.record(userId, "ATTACHMENT", "Uploaded attachment #" + attachment.getId());
        return convertToResponse(attachmentMapper.selectById(attachment.getId()), 0L);
    }

    @Override
    public List<AttachmentResponse> list(String fileType, Boolean unusedOnly, String keyword) {
        Long userId = requireCurrentUser();
        String normalizedType = normalizeFileType(fileType);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";

        LambdaQueryWrapper<NoteAttachment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteAttachment::getUserId, userId)
                .orderByDesc(NoteAttachment::getCreateTime)
                .orderByDesc(NoteAttachment::getId);

        if (StringUtils.hasText(normalizedType)) {
            queryWrapper.eq(NoteAttachment::getFileType, normalizedType);
        }

        List<NoteAttachment> attachments = attachmentMapper.selectList(queryWrapper);
        if (StringUtils.hasText(normalizedKeyword)) {
            attachments = attachments.stream()
                    .filter(attachment -> attachment.getOriginalName() != null
                            && attachment.getOriginalName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                    .toList();
        }

        Map<Long, Long> referenceCountByAttachmentId = countReferences(attachments.stream()
                .map(NoteAttachment::getId)
                .toList());

        return attachments.stream()
                .map(attachment -> convertToResponse(attachment, referenceCountByAttachmentId.getOrDefault(attachment.getId(), 0L)))
                .filter(response -> !Boolean.TRUE.equals(unusedOnly) || !Boolean.TRUE.equals(response.getUsed()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        Long userId = requireCurrentUser();
        NoteAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !userId.equals(attachment.getUserId())) {
            throw new BusinessException(404, "Attachment does not exist");
        }

        Long referenceCount = countReferences(List.of(attachmentId)).getOrDefault(attachmentId, 0L);
        if (referenceCount > 0) {
            throw new BusinessException(400, "Attachment is still referenced by notes");
        }

        attachmentMapper.deleteById(attachmentId);
        deleteStoredFile(attachment);
        operationLogService.record(userId, "ATTACHMENT", "Deleted unused attachment #" + attachmentId);
    }

    @Override
    @Transactional
    public void syncReferences(Long noteId, Collection<Long> attachmentIds, String content, String htmlContent) {
        Long userId = requireCurrentUser();
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getDeleted() != null && note.getDeleted() == 1)) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canEdit(note, userId)) {
            throw new BusinessException(403, "No permission to update note attachments");
        }

        Set<Long> nextAttachmentIds = new LinkedHashSet<>();
        if (attachmentIds != null) {
            attachmentIds.stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(nextAttachmentIds::add);
        }
        nextAttachmentIds.addAll(extractAttachmentIds(content));
        nextAttachmentIds.addAll(extractAttachmentIds(htmlContent));

        List<NoteAttachmentReference> existingReferences = referenceMapper.selectList(
                new LambdaQueryWrapper<NoteAttachmentReference>().eq(NoteAttachmentReference::getNoteId, noteId)
        );
        Set<Long> existingAttachmentIds = existingReferences.stream()
                .map(NoteAttachmentReference::getAttachmentId)
                .collect(Collectors.toSet());

        for (NoteAttachmentReference reference : existingReferences) {
            if (!nextAttachmentIds.contains(reference.getAttachmentId())) {
                referenceMapper.deleteById(reference.getId());
            }
        }

        for (Long attachmentId : nextAttachmentIds) {
            if (existingAttachmentIds.contains(attachmentId)) {
                continue;
            }

            NoteAttachment attachment = attachmentMapper.selectById(attachmentId);
            if (attachment == null || !userId.equals(attachment.getUserId())) {
                continue;
            }

            NoteAttachmentReference reference = new NoteAttachmentReference();
            reference.setNoteId(noteId);
            reference.setAttachmentId(attachmentId);
            referenceMapper.insert(reference);
        }
    }

    private Long requireCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return userId;
    }

    private String normalizeFileType(String fileType) {
        if (!StringUtils.hasText(fileType) || "ALL".equalsIgnoreCase(fileType.trim())) {
            return "";
        }

        String normalized = fileType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("IMAGE", "PDF", "WORD").contains(normalized)) {
            throw new BusinessException(400, "Unsupported attachment type");
        }
        return normalized;
    }

    private Path resolveAttachmentStorageDirectory() {
        return Paths.get(attachmentStorageDir).toAbsolutePath().normalize();
    }

    private String sanitizeOriginalName(String originalFilename) {
        String fallback = "attachment";
        if (!StringUtils.hasText(originalFilename)) {
            return fallback;
        }

        String normalized = Paths.get(originalFilename).getFileName().toString()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .trim();
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim().toLowerCase(Locale.ROOT) : "application/octet-stream";
    }

    private String resolveExtension(String originalName, String contentType) {
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalName.length() - 1) {
            extension = originalName.substring(dotIndex + 1).trim().toLowerCase(Locale.ROOT);
        }

        if (TYPE_BY_EXTENSION.containsKey(extension)) {
            return extension.equals("jpeg") ? "jpg" : extension;
        }

        String mappedExtension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (mappedExtension != null) {
            return mappedExtension;
        }

        throw new BusinessException(400, "Unsupported attachment file extension");
    }

    private String resolvePublicContentType(String contentType, String extension) {
        if (EXTENSION_BY_CONTENT_TYPE.containsKey(contentType)) {
            return contentType;
        }

        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private Map<Long, Long> countReferences(List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return new HashMap<>();
        }

        return referenceMapper.selectList(
                        new LambdaQueryWrapper<NoteAttachmentReference>()
                                .in(NoteAttachmentReference::getAttachmentId, attachmentIds)
                )
                .stream()
                .collect(Collectors.groupingBy(NoteAttachmentReference::getAttachmentId, Collectors.counting()));
    }

    private Set<Long> extractAttachmentIds(String value) {
        Set<Long> ids = new LinkedHashSet<>();
        if (!StringUtils.hasText(value)) {
            return ids;
        }

        Matcher matcher = ATTACHMENT_ID_PATTERN.matcher(value);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed attachment markers; user content should never break note saving.
            }
        }

        return ids;
    }

    private void deleteStoredFile(NoteAttachment attachment) {
        if (!StringUtils.hasText(attachment.getStoragePath())) {
            return;
        }

        Path rootDirectory = resolveAttachmentStorageDirectory();
        Path targetFile = rootDirectory.resolve(attachment.getStoragePath()).normalize();
        if (!targetFile.startsWith(rootDirectory)) {
            return;
        }

        try {
            Files.deleteIfExists(targetFile);
        } catch (IOException ignored) {
            // Metadata has already been removed; stale files can be cleaned manually if deletion fails.
        }
    }

    private AttachmentResponse convertToResponse(NoteAttachment attachment, Long referenceCount) {
        AttachmentResponse response = new AttachmentResponse();
        response.setId(attachment.getId());
        response.setUserId(attachment.getUserId());
        response.setOriginalName(attachment.getOriginalName());
        response.setFileName(attachment.getStoredName());
        response.setFileType(attachment.getFileType());
        response.setContentType(attachment.getContentType());
        response.setFileSize(attachment.getFileSize());
        response.setFileUrl(attachment.getFileUrl());
        response.setReferenceCount(referenceCount);
        response.setUsed(referenceCount != null && referenceCount > 0);
        response.setCreateTime(attachment.getCreateTime());
        response.setUpdateTime(attachment.getUpdateTime());
        return response;
    }
}
