package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.LoginRequest;
import com.ahy.knowledgepulse.dto.response.TokenResponse;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.impl.UserServiceImpl;
import com.ahy.knowledgepulse.util.JwtUtil;
import com.ahy.knowledgepulse.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void loginShouldReturnJwtPayload() {
        User user = new User();
        user.setId(7L);
        user.setUsername("tester");
        user.setPassword("encoded-password");
        user.setEmail("tester@example.com");
        user.setNickname("Tester");
        user.setRole("USER");

        LoginRequest request = new LoginRequest();
        request.setUsername("tester");
        request.setPassword("plain-password");

        ReflectionTestUtils.setField(userService, "expiration", 3600L);

        when(userMapper.findByUsername("tester")).thenReturn(user);
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken(7L, "tester")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(7L, "tester")).thenReturn("refresh-token");

        TokenResponse response = userService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUser().getUsername()).isEqualTo("tester");
    }

    @Test
    void uploadAvatarShouldStoreImageAndReturnPublicPath() throws IOException {
        Path tempDirectory = Files.createTempDirectory("knowledgepulse-avatar-test");
        ReflectionTestUtils.setField(userService, "avatarStorageDir", tempDirectory.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3, 4, 5}
        );

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(7L);

            String avatarPath = userService.uploadAvatar(file);

            assertThat(avatarPath).startsWith("/api/v1/media/avatars/user-7-").endsWith(".png");
            try (var storedFiles = Files.list(tempDirectory)) {
                assertThat(storedFiles.toList()).hasSize(1);
            }
        } finally {
            try (var paths = Files.walk(tempDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach((path) -> path.toFile().delete());
            }
        }
    }
}
