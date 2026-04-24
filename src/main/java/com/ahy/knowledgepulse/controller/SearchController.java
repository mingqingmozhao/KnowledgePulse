package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.dto.response.SearchResult;
import com.ahy.knowledgepulse.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "搜索", description = "全文搜索和标签搜索")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "全文搜索", description = "根据关键词搜索笔记")
    public Result<List<SearchResult>> search(@RequestParam String keyword) {
        return Result.success(searchService.search(keyword));
    }

    @GetMapping("/tag")
    @Operation(summary = "标签搜索", description = "根据标签搜索笔记")
    public Result<List<SearchResult>> searchByTag(@RequestParam String tagName) {
        return Result.success(searchService.searchByTag(tagName));
    }
}
