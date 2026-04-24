package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.FolderRequest;
import com.ahy.knowledgepulse.dto.response.FolderResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/folder")
@RequiredArgsConstructor
@Tag(name = "文件夹管理", description = "文件夹的创建、修改、删除和树结构获取")
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    @Operation(summary = "创建文件夹", description = "创建新文件夹")
    public Result<FolderResponse> createFolder(@Valid @RequestBody FolderRequest request) {
        return Result.success(folderService.createFolder(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新文件夹", description = "修改文件夹名称或父文件夹")
    public Result<FolderResponse> updateFolder(@PathVariable Long id, @Valid @RequestBody FolderRequest request) {
        return Result.success(folderService.updateFolder(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文件夹", description = "删除指定文件夹")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        folderService.deleteFolder(id);
        return Result.success(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取文件夹详情", description = "获取指定文件夹信息")
    public Result<FolderResponse> getFolderById(@PathVariable Long id) {
        return Result.success(folderService.getFolderById(id));
    }

    @GetMapping("/tree")
    @Operation(summary = "获取文件夹树", description = "获取当前用户的文件夹树结构")
    public Result<List<FolderResponse>> getFolderTree() {
        return Result.success(folderService.getFolderTree());
    }
}
