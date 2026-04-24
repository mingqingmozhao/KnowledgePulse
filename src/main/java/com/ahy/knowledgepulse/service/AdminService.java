package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.UpdateUserRoleRequest;
import com.ahy.knowledgepulse.dto.response.UserResponse;

import java.util.List;

public interface AdminService {

    List<UserResponse> listUsers();

    UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request);

    void warmupDailyInspirationCache();
}
