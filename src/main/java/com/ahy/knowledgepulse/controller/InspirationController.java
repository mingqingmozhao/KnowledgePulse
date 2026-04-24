package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.response.InspirationResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.InspirationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/daily-inspiration")
@RequiredArgsConstructor
@Tag(name = "每日灵感", description = "基于用户行为的智能推荐")
public class InspirationController {

    private final InspirationService inspirationService;

    @GetMapping
    @Operation(summary = "获取每日灵感", description = "获取基于用户最近编辑笔记的智能推荐")
    public Result<InspirationResponse> getDailyInspiration() {
        return Result.success(inspirationService.getDailyInspiration());
    }
}
