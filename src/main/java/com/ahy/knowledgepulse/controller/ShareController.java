package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.ShareRequest;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
@Tag(name = "分享", description = "笔记分享和权限管理")
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/{noteId}")
    @Operation(summary = "生成分享链接", description = "为笔记生成公开分享链接")
    public Result<String> generateShareLink(@PathVariable Long noteId, @RequestBody ShareRequest request) {
        return Result.success(shareService.generateShareLink(noteId, request));
    }

    @GetMapping("/public/{token}")
    @Operation(summary = "获取公开分享笔记", description = "通过分享链接获取笔记内容")
    public Result<NoteResponse> getSharedNote(@PathVariable String token, 
                                               @RequestParam(required = false) String password) {
        return Result.success(shareService.getSharedNote(token, password));
    }

    @DeleteMapping("/{noteId}")
    @Operation(summary = "取消分享", description = "取消笔记的分享状态")
    public Result<Void> revokeShare(@PathVariable Long noteId) {
        shareService.revokeShare(noteId);
        return Result.success(null);
    }
}
