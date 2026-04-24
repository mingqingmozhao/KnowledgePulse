package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.response.OperationLogResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/operation-log")
@RequiredArgsConstructor
@Tag(name = "Operation Log", description = "Current user's operation log history")
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping("/mine")
    @Operation(summary = "Get my operation logs", description = "Fetch the current user's recent operation logs")
    public Result<List<OperationLogResponse>> getMyLogs(
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String module
    ) {
        return Result.success(operationLogService.getCurrentUserLogs(limit, module));
    }
}
