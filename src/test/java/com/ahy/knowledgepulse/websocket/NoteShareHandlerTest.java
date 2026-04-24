package com.ahy.knowledgepulse.websocket;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoteShareHandlerTest {

    private final NoteShareHandler handler = new NoteShareHandler();

    @Test
    void handleNoteEditShouldEchoPayload() {
        Map<String, Object> payload = Map.of(
                "userId", 1L,
                "content", "updated content"
        );

        Map<String, Object> result = handler.handleNoteEdit(9L, payload);

        assertThat(result).isEqualTo(payload);
    }

    @Test
    void handleTypingShouldEchoPayload() {
        Map<String, Object> payload = Map.of(
                "userId", 2L,
                "nickname", "editor"
        );

        Map<String, Object> result = handler.handleTyping(9L, payload);

        assertThat(result).isEqualTo(payload);
    }

    @Test
    void handlePresenceShouldReturnOnlineRosterAndRemoveLeavingUser() {
        Map<String, Object> firstJoin = handler.handlePresence(9L, Map.of(
                "action", "join",
                "userId", 1L,
                "nickname", "owner"
        ));

        assertThat(firstJoin.get("users").toString()).contains("owner");

        Map<String, Object> secondJoin = handler.handlePresence(9L, Map.of(
                "action", "join",
                "userId", 2L,
                "nickname", "editor"
        ));

        assertThat(secondJoin.get("users").toString()).contains("owner", "editor");

        Map<String, Object> leave = handler.handlePresence(9L, Map.of(
                "action", "leave",
                "userId", 2L
        ));

        assertThat(leave.get("users").toString()).contains("owner");
        assertThat(leave.get("users").toString()).doesNotContain("editor");
    }
}
