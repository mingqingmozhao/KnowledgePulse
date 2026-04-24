package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.response.OperationLogResponse;

import java.util.List;

public interface OperationLogService {

    void record(Long userId, String module, String operation);

    void recordCurrentUser(String module, String operation);

    List<OperationLogResponse> getCurrentUserLogs(Integer limit, String module);

    List<OperationLogResponse> searchLogs(Long userId, String module, Integer limit);
}
