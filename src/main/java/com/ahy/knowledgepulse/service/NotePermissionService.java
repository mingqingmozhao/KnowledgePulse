package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteCollaborator;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.mapper.NoteCollaboratorMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotePermissionService {

    private final NoteCollaboratorMapper collaboratorMapper;
    private final UserMapper userMapper;

    public boolean canRead(Note note, Long userId) {
        if (note == null || userId == null) {
            return false;
        }

        if (canManage(note, userId)) {
            return true;
        }

        return getCollaboratorPermission(note.getId(), userId) != null;
    }

    public boolean canEdit(Note note, Long userId) {
        if (note == null || userId == null) {
            return false;
        }

        if (canManage(note, userId)) {
            return true;
        }

        String permission = getCollaboratorPermission(note.getId(), userId);
        return "EDIT".equalsIgnoreCase(permission) || "OWNER".equalsIgnoreCase(permission);
    }

    public boolean canManage(Note note, Long userId) {
        if (note == null || userId == null) {
            return false;
        }

        if (note.getUserId().equals(userId) || isAdmin(userId)) {
            return true;
        }

        String permission = getCollaboratorPermission(note.getId(), userId);
        return "OWNER".equalsIgnoreCase(permission);
    }

    public String getCollaboratorPermission(Long noteId, Long userId) {
        if (noteId == null || userId == null) {
            return null;
        }

        NoteCollaborator collaborator = collaboratorMapper.findByNoteIdAndUserId(noteId, userId);
        return collaborator == null ? null : collaborator.getPermission();
    }

    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }

        User user = userMapper.selectById(userId);
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
