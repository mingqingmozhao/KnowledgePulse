package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.CollaboratorRequest;
import com.ahy.knowledgepulse.dto.response.CollaboratorResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteCollaborator;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteCollaboratorMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.CollaboratorService;
import com.ahy.knowledgepulse.service.NotePermissionService;
import com.ahy.knowledgepulse.service.NotificationService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CollaboratorServiceImpl implements CollaboratorService {

    private static final Set<String> SUPPORTED_PERMISSIONS = Set.of("READ", "EDIT", "OWNER");

    private final NoteCollaboratorMapper collaboratorMapper;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;
    private final NotePermissionService notePermissionService;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public void addCollaborator(Long noteId, CollaboratorRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getDeleted() != null && note.getDeleted() == 1)) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "No permission to add collaborators");
        }

        User collaborator = userMapper.selectById(request.getUserId());
        if (collaborator == null) {
            throw new BusinessException(404, "User does not exist");
        }
        if (collaborator.getId().equals(note.getUserId())) {
            throw new BusinessException(400, "Owner is already a collaborator");
        }
        if (collaboratorMapper.findByNoteIdAndUserId(noteId, request.getUserId()) != null) {
            throw new BusinessException(400, "Collaborator already exists");
        }

        NoteCollaborator noteCollaborator = new NoteCollaborator();
        noteCollaborator.setNoteId(noteId);
        noteCollaborator.setUserId(request.getUserId());
        noteCollaborator.setPermission(normalizePermission(request.getPermission()));
        collaboratorMapper.insert(noteCollaborator);
        operationLogService.record(userId, "COLLABORATOR",
                "Added collaborator user#" + request.getUserId() + " to note#" + noteId);
        notificationService.notifyUser(
                request.getUserId(),
                userId,
                "COLLABORATION_INVITE",
                "新的协作邀请",
                "你被邀请协作笔记《" + note.getTitle() + "》，权限为 " + noteCollaborator.getPermission() + "。",
                noteId,
                "/note/" + noteId + "/edit"
        );
    }

    @Override
    @Transactional
    public void removeCollaborator(Long noteId, Long userIdToRemove) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "No permission to remove collaborators");
        }

        NoteCollaborator collaborator = collaboratorMapper.findByNoteIdAndUserId(noteId, userIdToRemove);
        if (collaborator != null) {
            collaboratorMapper.deleteById(collaborator.getId());
            operationLogService.record(userId, "COLLABORATOR",
                    "Removed collaborator user#" + userIdToRemove + " from note#" + noteId);
            notificationService.notifyUser(
                    userIdToRemove,
                    userId,
                    "COLLABORATION_REMOVED",
                    "协作权限已移除",
                    "你已不再是笔记《" + note.getTitle() + "》的协作者。",
                    noteId,
                    "/folder"
            );
        }
    }

    @Override
    @Transactional
    public void updatePermission(Long noteId, Long userId, String permission) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canManage(note, currentUserId)) {
            throw new BusinessException(403, "No permission to update collaborator permission");
        }

        NoteCollaborator collaborator = collaboratorMapper.findByNoteIdAndUserId(noteId, userId);
        if (collaborator == null) {
            throw new BusinessException(404, "Collaborator does not exist");
        }
        String previousPermission = collaborator.getPermission();
        collaborator.setPermission(normalizePermission(permission));
        collaboratorMapper.updateById(collaborator);
        operationLogService.record(currentUserId, "COLLABORATOR",
                "Updated collaborator user#" + userId + " permission on note#" + noteId + " to " + collaborator.getPermission());
        if (!collaborator.getPermission().equalsIgnoreCase(previousPermission)) {
            notificationService.notifyUser(
                    userId,
                    currentUserId,
                    "PERMISSION_CHANGED",
                    "协作权限已变更",
                    "你在笔记《" + note.getTitle() + "》中的权限从 " + previousPermission + " 调整为 " + collaborator.getPermission() + "。",
                    noteId,
                    "/note/" + noteId + "/edit"
            );
        }
    }

    @Override
    public List<CollaboratorResponse> getCollaborators(Long noteId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to view collaborators");
        }
        return collaboratorMapper.findResponsesByNoteId(noteId);
    }

    @Override
    public String getPermission(Long noteId, Long userId) {
        Note note = noteMapper.selectById(noteId);
        if (note != null && note.getUserId().equals(userId)) {
            return "OWNER";
        }
        return notePermissionService.getCollaboratorPermission(noteId, userId);
    }

    private String normalizePermission(String permission) {
        if (!StringUtils.hasText(permission)) {
            throw new BusinessException(400, "Collaborator permission is required");
        }

        String normalized = permission.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_PERMISSIONS.contains(normalized)) {
            throw new BusinessException(400, "Unsupported collaborator permission");
        }
        return normalized;
    }
}
