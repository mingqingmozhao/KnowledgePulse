package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAccessService {

    private static final Set<String> USER_MANAGEMENT_ROLES = Set.of("ADMIN");
    private static final Set<String> AUDIT_ROLES = Set.of("ADMIN", "AUDITOR");
    private static final Set<String> MAINTENANCE_ROLES = Set.of("ADMIN");

    private final UserMapper userMapper;

    public User requireUserManagementAccess() {
        return requireRole(USER_MANAGEMENT_ROLES, "Only administrators can manage users");
    }

    public User requireAuditAccess() {
        return requireRole(AUDIT_ROLES, "No permission to view operation logs");
    }

    public User requireMaintenanceAccess() {
        return requireRole(MAINTENANCE_ROLES, "Only administrators can run maintenance tasks");
    }

    private User requireRole(Set<String> allowedRoles, String errorMessage) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }

        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new BusinessException(401, "User does not exist");
        }

        String role = currentUser.getRole() == null
                ? "USER"
                : currentUser.getRole().trim().toUpperCase(Locale.ROOT);
        if (!allowedRoles.contains(role)) {
            throw new BusinessException(403, errorMessage);
        }
        return currentUser;
    }
}
