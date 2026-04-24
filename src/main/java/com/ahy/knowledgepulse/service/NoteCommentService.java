package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.CommentRequest;
import com.ahy.knowledgepulse.dto.response.CommentResponse;

import java.util.List;

public interface NoteCommentService {

    List<CommentResponse> getComments(Long noteId);

    CommentResponse createComment(Long noteId, CommentRequest request);

    void deleteComment(Long noteId, Long commentId);
}
