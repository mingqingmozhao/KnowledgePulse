package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.NoteRequest;
import com.ahy.knowledgepulse.dto.response.ExportPayload;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.dto.response.VersionResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteAttachmentReference;
import com.ahy.knowledgepulse.entity.NoteComment;
import com.ahy.knowledgepulse.entity.NoteFavorite;
import com.ahy.knowledgepulse.entity.NoteFolder;
import com.ahy.knowledgepulse.entity.NoteNotification;
import com.ahy.knowledgepulse.entity.NoteVersion;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteAttachmentReferenceMapper;
import com.ahy.knowledgepulse.mapper.NoteCollaboratorMapper;
import com.ahy.knowledgepulse.mapper.NoteCommentMapper;
import com.ahy.knowledgepulse.mapper.NoteFavoriteMapper;
import com.ahy.knowledgepulse.mapper.NoteFolderMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteNotificationMapper;
import com.ahy.knowledgepulse.mapper.NoteRelationMapper;
import com.ahy.knowledgepulse.mapper.NoteTagMapper;
import com.ahy.knowledgepulse.mapper.NoteVersionMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.NoteAttachmentService;
import com.ahy.knowledgepulse.service.NotePermissionService;
import com.ahy.knowledgepulse.service.NoteService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.service.TagService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NoteVersionMapper versionMapper;
    private final NoteFolderMapper folderMapper;
    private final NoteFavoriteMapper favoriteMapper;
    private final NoteCommentMapper commentMapper;
    private final NoteNotificationMapper notificationMapper;
    private final NoteAttachmentReferenceMapper attachmentReferenceMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteRelationMapper relationMapper;
    private final NoteCollaboratorMapper collaboratorMapper;
    private final UserMapper userMapper;
    private final TagService tagService;
    private final NoteAttachmentService attachmentService;
    private final NotePermissionService notePermissionService;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public NoteResponse createNote(NoteRequest request) {
        Long userId = requireCurrentUser();
        validateFolderOwnership(request.getFolderId(), userId);

        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(request.getTitle());
        note.setContent(defaultContent(request.getContent()));
        note.setHtmlContent(defaultHtml(request.getHtmlContent(), note.getContent()));
        note.setFolderId(request.getFolderId());
        note.setIsPublic(0);
        note.setDeleted(0);
        noteMapper.insert(note);

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            tagService.addTagsToNote(note.getId(), request.getTags());
            note = noteMapper.selectById(note.getId());
        }

        saveVersion(note);
        attachmentService.syncReferences(note.getId(), request.getAttachmentIds(), note.getContent(), note.getHtmlContent());
        operationLogService.record(userId, "NOTE", "Created note #" + note.getId());
        return convertToResponse(note, userId);
    }

    @Override
    @Transactional
    public NoteResponse updateNote(Long id, NoteRequest request) {
        Long userId = requireCurrentUser();
        Note note = requireActiveNote(id);

        if (!notePermissionService.canEdit(note, userId)) {
            throw new BusinessException(403, "No permission to edit this note");
        }

        if (!equalsFolder(note.getFolderId(), request.getFolderId())) {
            if (!notePermissionService.canManage(note, userId)) {
                throw new BusinessException(403, "Only the owner can move this note");
            }
            validateFolderOwnership(request.getFolderId(), note.getUserId());
            note.setFolderId(request.getFolderId());
        }

        note.setTitle(request.getTitle());
        note.setContent(defaultContent(request.getContent()));
        note.setHtmlContent(defaultHtml(request.getHtmlContent(), note.getContent()));
        noteMapper.updateById(note);

        if (request.getTags() != null) {
            tagService.updateNoteTags(note.getId(), request.getTags());
            note = noteMapper.selectById(note.getId());
        }

        saveVersion(note);
        attachmentService.syncReferences(note.getId(), request.getAttachmentIds(), note.getContent(), note.getHtmlContent());
        operationLogService.record(userId, "NOTE", "Updated note #" + note.getId());
        return convertToResponse(note, userId);
    }

    @Override
    @Transactional
    public void deleteNote(Long id) {
        Long userId = requireCurrentUser();
        Note note = requireActiveNote(id);

        if (!notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "Only the owner can delete this note");
        }

        note.setDeleted(1);
        note.setDeletedTime(LocalDateTime.now());
        note.setShareToken(null);
        note.setSharePassword(null);
        note.setIsPublic(0);
        noteMapper.updateById(note);
        operationLogService.record(userId, "NOTE", "Moved note #" + id + " to trash");
    }

    @Override
    public NoteResponse getNoteById(Long id) {
        Long userId = requireCurrentUser();
        Note note = requireNote(id);

        if (note.getDeleted() != null && note.getDeleted() == 1) {
            throw new BusinessException(404, "Note is in trash");
        }
        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to view this note");
        }

        return convertToResponse(note, userId);
    }

    @Override
    public List<NoteResponse> getNotesByFolder(Long folderId) {
        Long userId = requireCurrentUser();
        validateFolderOwnership(folderId, userId);
        return noteMapper.findByUserIdAndFolderId(userId, folderId)
                .stream()
                .map(note -> convertToResponse(note, userId))
                .toList();
    }

    @Override
    public List<NoteResponse> getAllNotes() {
        Long userId = requireCurrentUser();
        return noteMapper.findAccessibleNotes(userId)
                .stream()
                .map(note -> convertToResponse(note, userId))
                .toList();
    }

    @Override
    public List<NoteResponse> getFavoriteNotes() {
        Long userId = requireCurrentUser();
        return favoriteMapper.findByUserId(userId)
                .stream()
                .map(NoteFavorite::getNoteId)
                .map(noteMapper::selectById)
                .filter(note -> note != null && (note.getDeleted() == null || note.getDeleted() == 0))
                .filter(note -> notePermissionService.canRead(note, userId))
                .map(note -> convertToResponse(note, userId))
                .toList();
    }

    @Override
    @Transactional
    public NoteResponse getOrCreateDailyNote(LocalDate dailyNoteDate) {
        Long userId = requireCurrentUser();
        LocalDate targetDate = dailyNoteDate == null ? LocalDate.now() : dailyNoteDate;

        Note existing = noteMapper.findAnyByUserIdAndDailyNoteDate(userId, targetDate);
        if (existing != null) {
            if (existing.getDeleted() != null && existing.getDeleted() == 1) {
                existing.setDeleted(0);
                existing.setDeletedTime(null);
                noteMapper.updateById(existing);
                operationLogService.record(userId, "NOTE",
                        "Restored daily note #" + existing.getId() + " for " + targetDate);
            }
            return convertToResponse(existing, userId);
        }

        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(buildDailyNoteTitle(targetDate));
        note.setContent(buildDailyNoteContent(targetDate));
        note.setHtmlContent(defaultHtml(null, note.getContent()));
        note.setFolderId(null);
        note.setIsPublic(0);
        note.setDeleted(0);
        note.setDailyNoteDate(targetDate);
        noteMapper.insert(note);

        List<String> dailyTags = buildDailyNoteTags(targetDate);
        if (!dailyTags.isEmpty()) {
            tagService.addTagsToNote(note.getId(), dailyTags);
        }

        note = noteMapper.selectById(note.getId());
        saveVersion(note);
        operationLogService.record(userId, "NOTE",
                "Created daily note #" + note.getId() + " for " + targetDate);
        return convertToResponse(note, userId);
    }

    @Override
    public List<LocalDate> getDailyNoteDates(LocalDate startDate, LocalDate endDate) {
        Long userId = requireCurrentUser();
        LocalDate normalizedStart = startDate == null ? LocalDate.now().withDayOfMonth(1) : startDate;
        LocalDate normalizedEnd = endDate == null
                ? normalizedStart.withDayOfMonth(normalizedStart.lengthOfMonth())
                : endDate;

        if (normalizedEnd.isBefore(normalizedStart)) {
            throw new BusinessException(400, "Daily note date range is invalid");
        }

        return noteMapper.findDailyNotesByUserAndRange(userId, normalizedStart, normalizedEnd)
                .stream()
                .map(Note::getDailyNoteDate)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<VersionResponse> getNoteVersions(Long noteId) {
        Long userId = requireCurrentUser();
        Note note = requireActiveNote(noteId);
        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to view note history");
        }

        return versionMapper.findByNoteId(noteId)
                .stream()
                .map(this::convertVersionToResponse)
                .toList();
    }

    @Override
    @Transactional
    public NoteResponse restoreVersion(Long noteId, Integer version) {
        Long userId = requireCurrentUser();
        Note note = requireActiveNote(noteId);
        if (!notePermissionService.canEdit(note, userId)) {
            throw new BusinessException(403, "No permission to restore this version");
        }

        NoteVersion targetVersion = versionMapper.findByNoteId(noteId)
                .stream()
                .filter(item -> item.getVersion().equals(version))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "Version does not exist"));

        note.setContent(targetVersion.getContentSnapshot());
        note.setHtmlContent(defaultHtml(null, note.getContent()));
        noteMapper.updateById(note);
        saveVersion(note);
        attachmentService.syncReferences(noteId, null, note.getContent(), note.getHtmlContent());
        operationLogService.record(userId, "NOTE",
                "Restored note #" + noteId + " to version " + version);
        return convertToResponse(note, userId);
    }

    @Override
    public List<NoteResponse> getTrashNotes() {
        Long userId = requireCurrentUser();
        return noteMapper.findDeletedByUserId(userId)
                .stream()
                .map(note -> convertToResponse(note, userId))
                .toList();
    }

    @Override
    @Transactional
    public NoteResponse restoreFromTrash(Long noteId) {
        Long userId = requireCurrentUser();
        Note note = requireNote(noteId);
        if (!notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "No permission to restore this note");
        }
        if (note.getDeleted() == null || note.getDeleted() == 0) {
            throw new BusinessException(400, "Note is not in trash");
        }

        note.setDeleted(0);
        note.setDeletedTime(null);
        noteMapper.updateById(note);
        operationLogService.record(userId, "NOTE", "Restored note #" + noteId + " from trash");
        return convertToResponse(note, userId);
    }

    @Override
    @Transactional
    public NoteResponse favoriteNote(Long noteId) {
        Long userId = requireCurrentUser();
        Note note = requireActiveNote(noteId);
        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to favorite this note");
        }

        NoteFavorite favorite = favoriteMapper.findByNoteIdAndUserId(noteId, userId);
        if (favorite == null) {
            favorite = new NoteFavorite();
            favorite.setNoteId(noteId);
            favorite.setUserId(userId);
            favoriteMapper.insert(favorite);
            operationLogService.record(userId, "NOTE", "Favorited note #" + noteId);
        }

        return convertToResponse(note, userId);
    }

    @Override
    @Transactional
    public NoteResponse unfavoriteNote(Long noteId) {
        Long userId = requireCurrentUser();
        Note note = requireActiveNote(noteId);
        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to unfavorite this note");
        }

        favoriteMapper.deleteByNoteIdAndUserId(noteId, userId);
        operationLogService.record(userId, "NOTE", "Unfavorited note #" + noteId);
        return convertToResponse(note, userId);
    }

    @Override
    @Transactional
    public void permanentlyDeleteNote(Long noteId) {
        Long userId = requireCurrentUser();
        Note note = requireNote(noteId);
        if (!notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "No permission to permanently delete this note");
        }
        deleteNoteGraph(noteId);
        operationLogService.record(userId, "NOTE", "Permanently deleted note #" + noteId);
    }

    @Override
    public ExportPayload exportNote(Long noteId, String format) {
        Long userId = requireCurrentUser();
        Note note = requireActiveNote(noteId);
        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to export this note");
        }

        NoteResponse response = convertToResponse(note, userId);
        String normalizedFormat = StringUtils.hasText(format) ? format.trim().toUpperCase(Locale.ROOT) : "MARKDOWN";

        ExportPayload payload = switch (normalizedFormat) {
            case "WORD", "DOC", "DOCX" -> new ExportPayload(
                    buildFileName(response.getTitle(), "doc"),
                    "application/msword; charset=UTF-8",
                    withUtf8Bom(buildExportHtml(response))
            );
            case "PDF" -> new ExportPayload(
                    buildFileName(response.getTitle(), "pdf"),
                    "application/pdf",
                    buildPdfDocument(response)
            );
            case "MARKDOWN", "MD" -> new ExportPayload(
                    buildFileName(response.getTitle(), "md"),
                    "text/markdown; charset=UTF-8",
                    withUtf8Bom(defaultContent(response.getContent()))
            );
            default -> throw new BusinessException(400, "Unsupported export format");
        };
        operationLogService.record(userId, "NOTE", "Exported note #" + noteId + " as " + normalizedFormat);
        return payload;
    }

    @Override
    @Transactional
    public void cleanupExpiredDeletedNotes() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Note> expiredNotes = noteMapper.findExpiredDeletedNotes(cutoff);
        for (Note note : expiredNotes) {
            deleteNoteGraph(note.getId());
        }
    }

    private void deleteNoteGraph(Long noteId) {
        noteTagMapper.deleteByNoteId(noteId);
        versionMapper.deleteByNoteId(noteId);
        relationMapper.deleteByNoteId(noteId);
        collaboratorMapper.deleteByNoteId(noteId);
        favoriteMapper.deleteByNoteId(noteId);
        attachmentReferenceMapper.delete(new LambdaQueryWrapper<NoteAttachmentReference>().eq(NoteAttachmentReference::getNoteId, noteId));
        commentMapper.delete(new LambdaQueryWrapper<NoteComment>().eq(NoteComment::getNoteId, noteId));
        notificationMapper.delete(new LambdaQueryWrapper<NoteNotification>().eq(NoteNotification::getNoteId, noteId));
        noteMapper.deleteById(noteId);
    }

    private Long requireCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return userId;
    }

    private Note requireNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(404, "Note does not exist");
        }
        return note;
    }

    private Note requireActiveNote(Long noteId) {
        Note note = requireNote(noteId);
        if (note.getDeleted() != null && note.getDeleted() == 1) {
            throw new BusinessException(404, "Note does not exist");
        }
        return note;
    }

    private void validateFolderOwnership(Long folderId, Long ownerUserId) {
        if (folderId == null) {
            return;
        }

        NoteFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException(400, "Folder does not exist");
        }
        if (!folder.getUserId().equals(ownerUserId)) {
            throw new BusinessException(403, "Cannot save note into another user's folder");
        }
    }

    private boolean equalsFolder(Long currentFolderId, Long nextFolderId) {
        return currentFolderId == null ? nextFolderId == null : currentFolderId.equals(nextFolderId);
    }

    private String defaultContent(String content) {
        return content == null ? "" : content;
    }

    private String defaultHtml(String htmlContent, String content) {
        if (StringUtils.hasText(htmlContent)) {
            return htmlContent;
        }
        String escapedContent = content == null ? "" : content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<pre>" + escapedContent + "</pre>";
    }

    private String buildDailyNoteTitle(LocalDate date) {
        return date + " 每日笔记";
    }

    private String buildDailyNoteContent(LocalDate date) {
        return """
                # %s 每日笔记

                ## 今日聚焦
                - 
                - 

                ## 过程记录
                - 

                ## 灵感闪念
                - 

                ## 明日续写
                - 
                """.formatted(date);
    }

    private List<String> buildDailyNoteTags(LocalDate date) {
        return List.of(
                "每日笔记",
                date.getYear() + "年",
                date.getMonthValue() + "月"
        );
    }

    private void saveVersion(Note note) {
        Integer latestVersion = versionMapper.getLatestVersion(note.getId());
        NoteVersion version = new NoteVersion();
        version.setNoteId(note.getId());
        version.setVersion((latestVersion == null ? 0 : latestVersion) + 1);
        version.setContentSnapshot(note.getContent());
        versionMapper.insert(version);
    }

    private NoteResponse convertToResponse(Note note, Long currentUserId) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setTitle(note.getTitle());
        response.setContent(note.getContent());
        response.setHtmlContent(note.getHtmlContent());
        response.setFolderId(note.getFolderId());
        response.setIsPublic(note.getIsPublic());
        response.setDailyNoteDate(note.getDailyNoteDate());
        response.setDailyNote(note.getDailyNoteDate() != null);
        response.setDeleted(note.getDeleted());
        response.setDeletedTime(note.getDeletedTime());
        response.setCreateTime(note.getCreateTime());
        response.setUpdateTime(note.getUpdateTime());
        response.setTags(resolveTags(note.getId()));
        response.setCurrentUserCanManage(notePermissionService.canManage(note, currentUserId));
        response.setFavorited(false);

        if (note.getFolderId() != null) {
            NoteFolder folder = folderMapper.selectById(note.getFolderId());
            response.setFolderName(folder == null ? null : folder.getName());
        } else {
            response.setFolderName(null);
        }

        User owner = userMapper.selectById(note.getUserId());
        response.setOwnerUserId(note.getUserId());
        if (owner != null) {
            response.setOwnerUsername(owner.getUsername());
            response.setOwnerNickname(owner.getNickname());
            response.setOwnerAvatar(owner.getAvatar());
        }

        if (currentUserId != null) {
            if (note.getUserId().equals(currentUserId)) {
                response.setCurrentUserPermission("OWNER");
            } else if (notePermissionService.isAdmin(currentUserId)) {
                response.setCurrentUserPermission("ADMIN");
            } else {
                response.setCurrentUserPermission(notePermissionService.getCollaboratorPermission(note.getId(), currentUserId));
            }

            NoteFavorite favorite = favoriteMapper.findByNoteIdAndUserId(note.getId(), currentUserId);
            if (favorite != null) {
                response.setFavorited(true);
                response.setFavoriteTime(favorite.getCreateTime());
            }
        }

        return response;
    }

    private List<String> resolveTags(Long noteId) {
        List<String> tags = noteTagMapper.findTagsByNoteId(noteId);
        return tags == null ? Collections.emptyList() : tags;
    }

    private VersionResponse convertVersionToResponse(NoteVersion version) {
        VersionResponse response = new VersionResponse();
        response.setId(version.getId());
        response.setVersion(version.getVersion());
        response.setContentSnapshot(version.getContentSnapshot());
        response.setCreateTime(version.getCreateTime());
        return response;
    }

    private String buildFileName(String title, String extension) {
        String safeTitle = (StringUtils.hasText(title) ? title : "knowledgepulse-note")
                .replaceAll("[\\\\/:*?\"<>|]", "-")
                .trim();
        return safeTitle + "." + extension;
    }

    private byte[] withUtf8Bom(String content) {
        byte[] body = defaultContent(content).getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[body.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(body, 0, result, 3, body.length);
        return result;
    }

    private String buildExportHtml(NoteResponse note) {
        String html = StringUtils.hasText(note.getHtmlContent())
                ? note.getHtmlContent()
                : "<pre>" + escapeHtml(note.getContent()) + "</pre>";
        return "<!DOCTYPE html><html><head>"
                + "<meta charset=\"UTF-8\" />"
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />"
                + "<style>"
                + "@page{margin:24mm 18mm;}"
                + "body{font-family:\"Noto Sans SC\",\"Microsoft YaHei\",\"SimHei\",\"DengXian\",sans-serif;color:#1f2933;line-height:1.75;font-size:14px;}"
                + "h1{font-size:28px;margin:0 0 18px;color:#102a43;}"
                + "h2,h3{color:#243b53;margin-top:24px;}"
                + "p{margin:8px 0;}"
                + "pre{white-space:pre-wrap;word-break:break-word;background:#f6f8fb;border:1px solid #d9e2ec;border-radius:10px;padding:14px;}"
                + "code{font-family:\"Cascadia Mono\",\"Consolas\",monospace;background:#f6f8fb;border-radius:4px;padding:2px 4px;}"
                + "blockquote{border-left:4px solid #8aa4c0;margin:12px 0;padding:8px 14px;background:#f7fafc;color:#486581;}"
                + "table{border-collapse:collapse;width:100%;margin:12px 0;}"
                + "th,td{border:1px solid #bcccdc;padding:8px 10px;}"
                + "img{max-width:100%;height:auto;}"
                + "</style><title>"
                + escapeHtml(note.getTitle())
                + "</title></head><body><h1>"
                + escapeHtml(note.getTitle())
                + "</h1>"
                + html
                + "</body></html>";
    }

    private byte[] buildPdfDocument(NoteResponse note) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerPdfFonts(builder);
            builder.withHtmlContent(buildExportHtml(note), null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(500, "Failed to export PDF: " + ex.getMessage());
        }
    }

    private void registerPdfFonts(PdfRendererBuilder builder) {
        List<ExportFont> fonts = List.of(
                new ExportFont("Noto Sans SC", "C:\\Windows\\Fonts\\NotoSansSC.ttf"),
                new ExportFont("SimHei", "C:\\Windows\\Fonts\\simhei.ttf"),
                new ExportFont("DengXian", "C:\\Windows\\Fonts\\Deng.ttf"),
                new ExportFont("Noto Serif SC", "C:\\Windows\\Fonts\\NotoSerifSC.ttf"),
                new ExportFont("Noto Sans CJK SC", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.otf"),
                new ExportFont("WenQuanYi Micro Hei", "/usr/share/fonts/truetype/wqy/wqy-microhei.ttf")
        );

        for (ExportFont font : fonts) {
            File file = new File(font.path());
            if (file.isFile()) {
                builder.useFont(file, font.family());
            }
        }
    }

    private record ExportFont(String family, String path) {
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
