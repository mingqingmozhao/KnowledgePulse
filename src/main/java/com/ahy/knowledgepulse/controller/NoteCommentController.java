package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.CommentRequest;
import com.ahy.knowledgepulse.dto.response.CommentResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.NoteCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
@Tag(name = "Comment", description = "Note comments and comment notifications")
public class NoteCommentController {

    private final NoteCommentService commentService;

    @GetMapping("/{noteId}")
    @Operation(summary = "List note comments", description = "Fetch comments for an accessible note")
    public Result<List<CommentResponse>> listComments(@PathVariable Long noteId) {
        return Result.success(commentService.getComments(noteId));
    }

    @PostMapping("/{noteId}")
    @Operation(summary = "Create note comment", description = "Comment on an accessible note and notify collaborators")
    public Result<CommentResponse> createComment(
            @PathVariable Long noteId,
            @Valid @RequestBody CommentRequest request
    ) {
        return Result.success(commentService.createComment(noteId, request));
    }

    @DeleteMapping("/{noteId}/{commentId}")
    @Operation(summary = "Delete note comment", description = "Delete your own comment or manage comments as note owner")
    public Result<Void> deleteComment(@PathVariable Long noteId, @PathVariable Long commentId) {
        commentService.deleteComment(noteId, commentId);
        return Result.success(null);
    }
}
