package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.RelationRequest;
import com.ahy.knowledgepulse.dto.response.GraphData;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.GraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
@Tag(name = "知识图谱", description = "笔记关联关系和图谱数据")
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/{noteId}")
    @Operation(summary = "获取笔记图谱", description = "获取指定笔记的关联图谱")
    public Result<GraphData> getGraphData(@PathVariable Long noteId) {
        return Result.success(graphService.getGraphData(noteId));
    }

    @GetMapping("/global")
    @Operation(summary = "获取全局图谱", description = "获取当前用户的全部知识图谱")
    public Result<GraphData> getGlobalGraph() {
        return Result.success(graphService.getGlobalGraph());
    }

    @PostMapping("/relation")
    @Operation(summary = "添加关联关系", description = "在两个笔记之间添加关联关系")
    public Result<Void> addRelation(@Valid @RequestBody RelationRequest request) {
        graphService.addRelation(request);
        return Result.success(null);
    }

    @DeleteMapping("/relation/{id}")
    @Operation(summary = "删除关联关系", description = "删除指定的关联关系")
    public Result<Void> deleteRelation(@PathVariable Long id) {
        graphService.deleteRelation(id);
        return Result.success(null);
    }
}
