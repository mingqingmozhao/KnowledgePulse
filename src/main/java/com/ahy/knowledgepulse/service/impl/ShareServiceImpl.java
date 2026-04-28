package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.ShareRequest;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteCollaborator;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteCollaboratorMapper;
import com.ahy.knowledgepulse.mapper.NoteFolderMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteTagMapper;
import com.ahy.knowledgepulse.service.NotePermissionService;
import com.ahy.knowledgepulse.service.NotificationService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.service.ShareService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final NoteMapper noteMapper;
    private final NoteFolderMapper folderMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteCollaboratorMapper collaboratorMapper;
    private final NotePermissionService notePermissionService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;

    @Value("${knowledgepulse.public-app-url:}")
    private String publicAppUrl;

    @Override
    @Transactional
    public String generateShareLink(Long noteId, ShareRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getDeleted() != null && note.getDeleted() == 1)) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "No permission to share this note");
        }

        int shareMode = request.getIsPublic() == null ? 1 : request.getIsPublic();
        if (shareMode < 0 || shareMode > 2) {
            throw new BusinessException(400, "Share mode must be 0, 1 or 2");
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        note.setShareToken(token);
        note.setIsPublic(shareMode);
        if (StringUtils.hasText(request.getPassword())) {
            note.setSharePassword(passwordEncoder.encode(request.getPassword()));
        } else {
            note.setSharePassword(null);
        }
        noteMapper.updateById(note);
        operationLogService.record(userId, "SHARE",
                "Generated share link for note#" + noteId + " with mode " + shareMode);
        notificationService.notifyUsers(
                collectAudience(note, userId),
                userId,
                "SHARE_CREATED",
                "笔记分享已开启",
                "《" + note.getTitle() + "》已开启" + shareModeLabel(shareMode) + "，团队成员可在编辑页查看分享状态。",
                noteId,
                "/note/" + noteId + "/edit"
        );
        return buildShareLink(token);
    }

    @Override
    public NoteResponse getSharedNote(String token, String password) {
        Note note = noteMapper.findByShareToken(token);
        if (note == null || (note.getDeleted() != null && note.getDeleted() == 1)) {
            throw new BusinessException(404, "Share link does not exist");
        }

        if (Integer.valueOf(0).equals(note.getIsPublic())) {
            throw new BusinessException(403, "Share has been revoked");
        }

        if (Integer.valueOf(2).equals(note.getIsPublic()) && SecurityUtil.getCurrentUserId() == null) {
            throw new BusinessException(401, "Login is required to access this share");
        }

        if (StringUtils.hasText(note.getSharePassword())) {
            if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, note.getSharePassword())) {
                throw new BusinessException(401, "Share password is required");
            }
        }

        return convertToResponse(note);
    }

    @Override
    @Transactional
    public void revokeShare(Long noteId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "No permission to revoke this share");
        }
        note.setShareToken(null);
        note.setSharePassword(null);
        note.setIsPublic(0);
        noteMapper.updateById(note);
        operationLogService.record(userId, "SHARE", "Revoked share link for note#" + noteId);
        notificationService.notifyUsers(
                collectAudience(note, userId),
                userId,
                "SHARE_REVOKED",
                "笔记分享已关闭",
                "《" + note.getTitle() + "》的分享链接已关闭。",
                noteId,
                "/note/" + noteId + "/edit"
        );
    }

    private Set<Long> collectAudience(Note note, Long actorUserId) {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(note.getUserId());

        for (NoteCollaborator collaborator : collaboratorMapper.findByNoteId(note.getId())) {
            recipients.add(collaborator.getUserId());
        }

        recipients.remove(actorUserId);
        return recipients;
    }

    private String shareModeLabel(int shareMode) {
        if (shareMode == 2) {
            return "登录可见分享";
        }

        if (shareMode == 1) {
            return "公开分享";
        }

        return "分享";
    }

    private String buildShareLink(String token) {
        String sharePath = "/share/" + token;

        if (!StringUtils.hasText(publicAppUrl)) {
            return sharePath;
        }

        String baseUrl = publicAppUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + sharePath;
    }

    private NoteResponse convertToResponse(Note note) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setTitle(note.getTitle());
        response.setContent(note.getContent());
        response.setHtmlContent(note.getHtmlContent());
        response.setFolderId(note.getFolderId());
        if (note.getFolderId() != null) {
            var folder = folderMapper.selectById(note.getFolderId());
            response.setFolderName(folder == null ? null : folder.getName());
        }
        response.setTags(resolveTags(note.getId()));
        response.setIsPublic(note.getIsPublic());
        response.setDeleted(note.getDeleted());
        response.setDeletedTime(note.getDeletedTime());
        response.setCreateTime(note.getCreateTime());
        response.setUpdateTime(note.getUpdateTime());
        return response;
    }

    private List<String> resolveTags(Long noteId) {
        List<String> tags = noteTagMapper.findTagsByNoteId(noteId);
        return tags == null ? List.of() : tags;
    }
}
