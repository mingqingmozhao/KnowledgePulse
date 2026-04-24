package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.UpdateUserRoleRequest;
import com.ahy.knowledgepulse.dto.response.OperationLogResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.dto.response.UserResponse;
import com.ahy.knowledgepulse.service.AdminAccessService;
import com.ahy.knowledgepulse.service.AdminService;
import com.ahy.knowledgepulse.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations, logs and maintenance")
public class AdminController {

    private final AdminService adminService;
    private final AdminAccessService adminAccessService;
    private final OperationLogService operationLogService;

    @GetMapping("/users")
    @Operation(summary = "List users", description = "List all users for administrative management")
    public Result<List<UserResponse>> listUsers() {
        return Result.success(adminService.listUsers());
    }

    @PutMapping("/users/{id}/role")
    @Operation(summary = "Update user role", description = "Update a user's system role")
    public Result<UserResponse> updateUserRole(@PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequest request) {
        return Result.success(adminService.updateUserRole(id, request));
    }

    @GetMapping("/logs")
    @Operation(summary = "Search operation logs", description = "Query operation logs by user or module")
    public Result<List<OperationLogResponse>> getOperationLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "100") Integer limit
    ) {
        adminAccessService.requireAuditAccess();
        return Result.success(operationLogService.searchLogs(userId, module, limit));
    }

    @PostMapping("/jobs/daily-inspiration/warmup")
    @Operation(summary = "Warm up daily inspiration cache", description = "Precompute today's inspiration cache for all users")
    public Result<Void> warmupDailyInspirationCache() {
        adminService.warmupDailyInspirationCache();
        return Result.success(null);
    }
}
