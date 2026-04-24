package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.LoginRequest;
import com.ahy.knowledgepulse.dto.request.RegisterRequest;
import com.ahy.knowledgepulse.dto.request.UpdateProfileRequest;
import com.ahy.knowledgepulse.dto.response.TokenResponse;
import com.ahy.knowledgepulse.dto.response.UserResponse;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.service.UserService;
import com.ahy.knowledgepulse.util.JwtUtil;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final String PUBLIC_AVATAR_PREFIX = "/api/v1/media/avatars/";
    private static final Set<String> SUPPORTED_AVATAR_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );
    private static final Map<String, String> AVATAR_EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OperationLogService operationLogService;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${knowledgepulse.avatar-storage-dir:storage/avatars}")
    private String avatarStorageDir;

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(401, "User does not exist");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "Incorrect password");
        }
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());
        operationLogService.record(user.getId(), "AUTH", "User login");
        return new TokenResponse(accessToken, refreshToken, expiration, convertToResponse(user));
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(400, "Username already exists");
        }
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new BusinessException(400, "Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setRole("USER");
        userMapper.insert(user);
        operationLogService.record(user.getId(), "AUTH", "User register");
        return convertToResponse(user);
    }

    @Override
    public UserResponse getCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return convertToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "User is not authenticated");
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            String normalizedAvatar = normalizeAvatarValue(request.getAvatar());
            deleteManagedAvatarIfChanged(user.getAvatar(), normalizedAvatar);
            user.setAvatar(normalizedAvatar);
        }
        if (request.getEmail() != null) {
            User existing = userMapper.findByEmail(request.getEmail());
            if (existing != null && !existing.getId().equals(userId)) {
                throw new BusinessException(400, "Email already in use");
            }
            user.setEmail(request.getEmail());
        }

        if (StringUtils.hasText(request.getNewPassword())) {
            if (!StringUtils.hasText(request.getCurrentPassword())) {
                throw new BusinessException(400, "Current password is required");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException(400, "Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        userMapper.updateById(user);
        operationLogService.record(userId, "USER", "Updated profile");
        return convertToResponse(user);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "Avatar file is required");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException(400, "Avatar image must be 2MB or smaller");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!SUPPORTED_AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(400, "Only PNG, JPG, WEBP or GIF avatars are supported");
        }

        String extension = AVATAR_EXTENSION_BY_CONTENT_TYPE.get(contentType);
        Path storageDirectory = resolveAvatarStorageDirectory();
        String fileName = "user-" + userId + "-" + UUID.randomUUID() + "." + extension;
        Path targetFile = storageDirectory.resolve(fileName).normalize();

        if (!targetFile.startsWith(storageDirectory)) {
            throw new BusinessException(400, "Invalid avatar path");
        }

        try {
            Files.createDirectories(storageDirectory);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(500, "Failed to save avatar: " + ex.getMessage());
        }

        operationLogService.record(userId, "USER", "Uploaded avatar");
        return PUBLIC_AVATAR_PREFIX + fileName;
    }

    @Override
    public List<UserResponse> searchUsers(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        return userMapper.searchUsers(keyword.trim(), 10, currentUserId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Override
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    private String normalizeAvatarValue(String avatar) {
        if (!StringUtils.hasText(avatar)) {
            return null;
        }

        String normalized = avatar.trim();
        if (normalized.length() > 255) {
            throw new BusinessException(400, "Avatar value is too long");
        }
        return normalized;
    }

    private void deleteManagedAvatarIfChanged(String currentAvatar, String nextAvatar) {
        if (!StringUtils.hasText(currentAvatar)) {
            return;
        }
        if (currentAvatar.equals(nextAvatar)) {
            return;
        }
        if (!currentAvatar.startsWith(PUBLIC_AVATAR_PREFIX)) {
            return;
        }

        String fileName = currentAvatar.substring(PUBLIC_AVATAR_PREFIX.length());
        if (!StringUtils.hasText(fileName)) {
            return;
        }

        Path storageDirectory = resolveAvatarStorageDirectory();
        Path filePath = storageDirectory.resolve(fileName).normalize();
        if (!filePath.startsWith(storageDirectory)) {
            return;
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            return;
        }
    }

    private Path resolveAvatarStorageDirectory() {
        return Paths.get(avatarStorageDir).toAbsolutePath().normalize();
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
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
