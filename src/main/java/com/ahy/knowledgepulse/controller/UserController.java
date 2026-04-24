package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.LoginRequest;
import com.ahy.knowledgepulse.dto.request.RegisterRequest;
import com.ahy.knowledgepulse.dto.request.UpdateProfileRequest;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.dto.response.TokenResponse;
import com.ahy.knowledgepulse.dto.response.UserResponse;
import com.ahy.knowledgepulse.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User registration, login and profile management")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register", description = "Create a new user account")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate and return JWT tokens")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @GetMapping("/info")
    @Operation(summary = "Current user", description = "Fetch current authenticated user profile")
    public Result<UserResponse> getCurrentUser() {
        return Result.success(userService.getCurrentUser());
    }

    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Search users by nickname, username or email")
    public Result<List<UserResponse>> searchUsers(@RequestParam String keyword) {
        return Result.success(userService.searchUsers(keyword));
    }

    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar", description = "Upload an avatar image and return the public path")
    public Result<String> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return Result.success(userService.uploadAvatar(file));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile", description = "Update nickname, email, avatar or password")
    public Result<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(request));
    }
}
