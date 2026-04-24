package com.ahy.knowledgepulse.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@Slf4j
public class NoteShareHandler {

    private static final long PRESENCE_TTL_MILLIS = 25_000L;
    private final Map<Long, Map<Long, PresenceMember>> presenceByNoteId = new ConcurrentHashMap<>();

    @MessageMapping("/note/{noteId}/edit")
    @SendTo("/topic/note/{noteId}")
    public Map<String, Object> handleNoteEdit(@DestinationVariable Long noteId, Map<String, Object> payload) {
        log.info("Received edit for note {}: {}", noteId, payload);
        return payload;
    }

    @MessageMapping("/note/{noteId}/cursor")
    @SendTo("/topic/note/{noteId}/cursor")
    public Map<String, Object> handleCursorMove(@DestinationVariable Long noteId, Map<String, Object> payload) {
        log.info("Received cursor move for note {}: {}", noteId, payload);
        return payload;
    }

    @MessageMapping("/note/{noteId}/typing")
    @SendTo("/topic/note/{noteId}/typing")
    public Map<String, Object> handleTyping(@DestinationVariable Long noteId, Map<String, Object> payload) {
        log.info("Received typing notification for note {}: {}", noteId, payload);
        return payload;
    }

    @MessageMapping("/note/{noteId}/presence")
    @SendTo("/topic/note/{noteId}/presence")
    public Map<String, Object> handlePresence(@DestinationVariable Long noteId, Map<String, Object> payload) {
        Long userId = parseLong(payload.get("userId"));
        String action = String.valueOf(payload.getOrDefault("action", "heartbeat"));

        if (userId == null || userId <= 0) {
            return buildPresencePayload(noteId);
        }

        Map<Long, PresenceMember> notePresence = presenceByNoteId.computeIfAbsent(noteId, ignored -> new ConcurrentHashMap<>());
        if ("leave".equalsIgnoreCase(action)) {
            notePresence.remove(userId);
        } else {
            notePresence.put(userId, new PresenceMember(
                    userId,
                    stringValue(payload.get("username")),
                    stringValue(payload.get("nickname")),
                    stringValue(payload.get("avatar")),
                    stringValue(payload.getOrDefault("permission", "EDIT")),
                    Instant.now().toEpochMilli()
            ));
        }

        return buildPresencePayload(noteId);
    }

    private Map<String, Object> buildPresencePayload(Long noteId) {
        Map<Long, PresenceMember> notePresence = presenceByNoteId.computeIfAbsent(noteId, ignored -> new ConcurrentHashMap<>());
        long now = Instant.now().toEpochMilli();
        notePresence.entrySet().removeIf(entry -> now - entry.getValue().lastSeenAt() > PRESENCE_TTL_MILLIS);

        List<Map<String, Object>> users = notePresence.values().stream()
                .sorted(Comparator.comparingLong(PresenceMember::lastSeenAt).reversed())
                .map(member -> Map.<String, Object>of(
                        "userId", member.userId(),
                        "username", Objects.toString(member.username(), ""),
                        "nickname", Objects.toString(member.nickname(), ""),
                        "avatar", Objects.toString(member.avatar(), ""),
                        "permission", Objects.toString(member.permission(), "EDIT"),
                        "active", true,
                        "lastSeenAt", member.lastSeenAt()
                ))
                .toList();

        return Map.of(
                "noteId", noteId,
                "users", users,
                "timestamp", now
        );
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value);
    }

    private record PresenceMember(
            Long userId,
            String username,
            String nickname,
            String avatar,
            String permission,
            long lastSeenAt
    ) {
    }
}
