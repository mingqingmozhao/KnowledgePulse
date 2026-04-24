package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.response.DashboardResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "个人数据统计和可视化")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "获取仪表盘数据", description = "获取用户的笔记统计信息")
    public Result<DashboardResponse> getDashboard() {
        return Result.success(dashboardService.getDashboardData());
    }
}
