package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.response.NotificationResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Notification inbox for sharing, collaboration, comments and permissions")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List notifications", description = "Fetch current user's notification inbox")
    public Result<List<NotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "30") Integer limit,
            @RequestParam(defaultValue = "false") Boolean unreadOnly
    ) {
        return Result.success(notificationService.getCurrentUserNotifications(limit, unreadOnly));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count unread notifications", description = "Fetch current user's unread notification count")
    public Result<Map<String, Long>> countUnread() {
        return Result.success(Map.of("count", notificationService.countCurrentUserUnread()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark notification read", description = "Mark a single notification as read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success(null);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications read", description = "Mark all current user's notifications as read")
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return Result.success(null);
    }
}
