package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.CollaboratorRequest;
import com.ahy.knowledgepulse.dto.response.CollaboratorResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.CollaboratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/collaborator")
@RequiredArgsConstructor
@Tag(name = "协作", description = "协作者管理和权限控制")
public class CollaboratorController {

    private final CollaboratorService collaboratorService;

    @PostMapping("/{noteId}")
    @Operation(summary = "添加协作者", description = "为笔记添加协作者")
    public Result<Void> addCollaborator(@PathVariable Long noteId, @Valid @RequestBody CollaboratorRequest request) {
        collaboratorService.addCollaborator(noteId, request);
        return Result.success(null);
    }

    @DeleteMapping("/{noteId}/{userId}")
    @Operation(summary = "移除协作者", description = "从笔记中移除协作者")
    public Result<Void> removeCollaborator(@PathVariable Long noteId, @PathVariable Long userId) {
        collaboratorService.removeCollaborator(noteId, userId);
        return Result.success(null);
    }

    @PutMapping("/{noteId}/{userId}")
    @Operation(summary = "更新权限", description = "更新协作者的权限")
    public Result<Void> updatePermission(@PathVariable Long noteId, 
                                         @PathVariable Long userId, 
                                         @RequestParam String permission) {
        collaboratorService.updatePermission(noteId, userId, permission);
        return Result.success(null);
    }

    @GetMapping("/{noteId}")
    @Operation(summary = "获取协作者列表", description = "获取笔记的协作者列表")
    public Result<List<CollaboratorResponse>> getCollaborators(@PathVariable Long noteId) {
        return Result.success(collaboratorService.getCollaborators(noteId));
    }
}
