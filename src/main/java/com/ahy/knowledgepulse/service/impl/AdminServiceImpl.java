package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.UpdateUserRoleRequest;
import com.ahy.knowledgepulse.dto.response.UserResponse;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.AdminAccessService;
import com.ahy.knowledgepulse.service.AdminService;
import com.ahy.knowledgepulse.service.InspirationService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN", "AUDITOR");

    private final UserMapper userMapper;
    private final AdminAccessService adminAccessService;
    private final OperationLogService operationLogService;
    private final InspirationService inspirationService;

    @Override
    public List<UserResponse> listUsers() {
        adminAccessService.requireUserManagementAccess();
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getCreateTime))
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Override
    public UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request) {
        User currentAdmin = adminAccessService.requireUserManagementAccess();
        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            throw new BusinessException(404, "User does not exist");
        }

        String role = normalizeRole(request.getRole());
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException(400, "Unsupported role: " + role);
        }

        if (currentAdmin.getId().equals(targetUser.getId()) && !"ADMIN".equals(role) && countAdmins() <= 1) {
            throw new BusinessException(400, "At least one ADMIN user must remain in the system");
        }

        targetUser.setRole(role);
        userMapper.updateById(targetUser);
        operationLogService.record(currentAdmin.getId(), "ADMIN",
                "Updated role for user#" + targetUser.getId() + " to " + role);
        return convertToResponse(targetUser);
    }

    @Override
    public void warmupDailyInspirationCache() {
        User currentAdmin = adminAccessService.requireMaintenanceAccess();
        inspirationService.warmupDailyInspirations();
        operationLogService.record(currentAdmin.getId(), "ADMIN", "Triggered daily inspiration cache warmup");
    }

    private long countAdmins() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN"));
    }

    private String normalizeRole(String role) {
        return role == null ? "USER" : role.trim().toUpperCase(Locale.ROOT);
    }

    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setAvatar(user.getAvatar());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setCreateTime(user.getCreateTime());
        return response;
    }
}
