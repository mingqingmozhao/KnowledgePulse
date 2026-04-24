package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.CommentRequest;
import com.ahy.knowledgepulse.dto.response.CommentResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteCollaborator;
import com.ahy.knowledgepulse.entity.NoteComment;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteCollaboratorMapper;
import com.ahy.knowledgepulse.mapper.NoteCommentMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.NoteCommentService;
import com.ahy.knowledgepulse.service.NotePermissionService;
import com.ahy.knowledgepulse.service.NotificationService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NoteCommentServiceImpl implements NoteCommentService {

    private final NoteCommentMapper commentMapper;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;
    private final NoteCollaboratorMapper collaboratorMapper;
    private final NotePermissionService notePermissionService;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;

    @Override
    public List<CommentResponse> getComments(Long noteId) {
        Long userId = requireCurrentUser();
        Note note = requireReadableNote(noteId, userId);

        LambdaQueryWrapper<NoteComment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NoteComment::getNoteId, note.getId())
                .orderByAsc(NoteComment::getCreateTime)
                .orderByAsc(NoteComment::getId);

        return commentMapper.selectList(queryWrapper)
                .stream()
                .map(comment -> convertToResponse(comment, note, userId))
                .toList();
    }

    @Override
    @Transactional
    public CommentResponse createComment(Long noteId, CommentRequest request) {
        Long userId = requireCurrentUser();
        Note note = requireReadableNote(noteId, userId);

        NoteComment comment = new NoteComment();
        comment.setNoteId(note.getId());
        comment.setUserId(userId);
        comment.setContent(request.getContent().trim());
        commentMapper.insert(comment);

        operationLogService.record(userId, "COMMENT", "Commented on note #" + note.getId());
        notificationService.notifyUsers(
                collectAudience(note, userId),
                userId,
                "COMMENT",
                "有新的评论",
                "《" + note.getTitle() + "》收到了新的评论。",
                note.getId(),
                "/note/" + note.getId() + "/edit"
        );

        return convertToResponse(commentMapper.selectById(comment.getId()), note, userId);
    }

    @Override
    @Transactional
    public void deleteComment(Long noteId, Long commentId) {
        Long userId = requireCurrentUser();
        Note note = requireReadableNote(noteId, userId);
        NoteComment comment = commentMapper.selectById(commentId);

        if (comment == null || !note.getId().equals(comment.getNoteId())) {
            throw new BusinessException(404, "Comment does not exist");
        }

        if (!userId.equals(comment.getUserId()) && !notePermissionService.canManage(note, userId)) {
            throw new BusinessException(403, "No permission to delete this comment");
        }

        commentMapper.deleteById(commentId);
        operationLogService.record(userId, "COMMENT", "Deleted comment #" + commentId + " from note #" + note.getId());
    }

    private Long requireCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return userId;
    }

    private Note requireReadableNote(Long noteId, Long userId) {
        Note note = noteMapper.selectById(noteId);

        if (note == null || (note.getDeleted() != null && note.getDeleted() == 1)) {
            throw new BusinessException(404, "Note does not exist");
        }

        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to view this note");
        }

        return note;
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

    private CommentResponse convertToResponse(NoteComment comment, Note note, Long currentUserId) {
        User author = userMapper.selectById(comment.getUserId());
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setNoteId(comment.getNoteId());
        response.setUserId(comment.getUserId());
        response.setContent(comment.getContent());
        response.setCreateTime(comment.getCreateTime());
        response.setUpdateTime(comment.getUpdateTime());
        response.setCanDelete(currentUserId.equals(comment.getUserId()) || notePermissionService.canManage(note, currentUserId));

        if (author != null) {
            response.setUsername(author.getUsername());
            response.setNickname(author.getNickname());
            response.setAvatar(author.getAvatar());
        }

        return response;
    }
}
