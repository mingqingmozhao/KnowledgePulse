package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.OperationLogResponse;
import com.ahy.knowledgepulse.entity.OperationLog;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.OperationLogMapper;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OperationLogMapper operationLogMapper;

    @Override
    public void record(Long userId, String module, String operation) {
        if (userId == null || !StringUtils.hasText(module) || !StringUtils.hasText(operation)) {
            return;
        }

        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(userId);
            logEntry.setModule(module.trim().toUpperCase());
            logEntry.setOperation(trimOperation(operation));
            operationLogMapper.insert(logEntry);
        } catch (Exception ex) {
            log.warn("Failed to persist operation log for user {} module {}: {}", userId, module, ex.getMessage());
        }
    }

    @Override
    public void recordCurrentUser(String module, String operation) {
        record(SecurityUtil.getCurrentUserId(), module, operation);
    }

    @Override
    public List<OperationLogResponse> getCurrentUserLogs(Integer limit, String module) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return searchLogs(currentUserId, module, limit);
    }

    @Override
    public List<OperationLogResponse> searchLogs(Long userId, String module, Integer limit) {
        LambdaQueryWrapper<OperationLog> queryWrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            queryWrapper.eq(OperationLog::getUserId, userId);
        }
        if (StringUtils.hasText(module)) {
            queryWrapper.eq(OperationLog::getModule, module.trim().toUpperCase());
        }

        queryWrapper.orderByDesc(OperationLog::getCreateTime);
        queryWrapper.last("LIMIT " + normalizeLimit(limit));

        return operationLogMapper.selectList(queryWrapper)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String trimOperation(String operation) {
        String normalized = operation.trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private OperationLogResponse convertToResponse(OperationLog operationLog) {
        OperationLogResponse response = new OperationLogResponse();
        response.setId(operationLog.getId());
        response.setUserId(operationLog.getUserId());
        response.setModule(operationLog.getModule());
        response.setOperation(operationLog.getOperation());
        response.setCreateTime(operationLog.getCreateTime());
        return response;
    }
}
