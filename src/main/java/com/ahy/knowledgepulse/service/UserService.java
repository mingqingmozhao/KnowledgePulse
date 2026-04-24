package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.LoginRequest;
import com.ahy.knowledgepulse.dto.request.RegisterRequest;
import com.ahy.knowledgepulse.dto.request.UpdateProfileRequest;
import com.ahy.knowledgepulse.dto.response.TokenResponse;
import com.ahy.knowledgepulse.dto.response.UserResponse;
import com.ahy.knowledgepulse.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    TokenResponse login(LoginRequest request);

    UserResponse register(RegisterRequest request);

    UserResponse getCurrentUser();

    UserResponse updateProfile(UpdateProfileRequest request);

    String uploadAvatar(MultipartFile file);

    List<UserResponse> searchUsers(String keyword);

    User getUserById(Long userId);

    User getUserByUsername(String username);
}
