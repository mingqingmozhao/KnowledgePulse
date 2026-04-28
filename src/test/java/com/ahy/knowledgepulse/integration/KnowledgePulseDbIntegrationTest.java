package com.ahy.knowledgepulse.integration;

import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgePulseDbIntegrationTest {

    private static final String API_PREFIX = "/api/v1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of(
                "note_relation",
                "note_collaborator",
                "note_favorite",
                "note_attachment_reference",
                "note_attachment",
                "note_notification",
                "note_comment",
                "note_tag",
                "note_version",
                "note_template",
                "operation_log",
                "note",
                "note_folder",
                "user"
        )) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    void loginShouldAuthenticateAgainstRealDatabase() throws Exception {
        insertUser("alice", "password123", "USER");

        MvcResult result = mockMvc.perform(post(API_PREFIX + "/user/login")
                        .contextPath(API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = readBody(result);
        assertThat(body.path("code").asInt()).isEqualTo(200);
        assertThat(body.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(body.path("data").path("user").path("username").asText()).isEqualTo("alice");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class)).isEqualTo(1L);
    }

    @Test
    void noteCrudTrashRestoreAndPermanentDeleteShouldPersistInDatabase() throws Exception {
        insertUser("writer", "password123", "USER");
        String token = login("writer", "password123");

        long noteId = createNote(token, "Architecture", List.of("java", "spring"));

        mockMvc.perform(put(API_PREFIX + "/note/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notePayload(
                                "Architecture v2",
                                "# updated",
                                "<h1>updated</h1>",
                                List.of("java", "backend"),
                                null
                        ))))
                .andExpect(status().isOk());

        Note updated = noteMapper.selectById(noteId);
        assertThat(updated.getTitle()).isEqualTo("Architecture v2");
        assertThat(updated.getDeleted()).isEqualTo(0);

        mockMvc.perform(delete(API_PREFIX + "/note/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());

        Note deleted = noteMapper.selectById(noteId);
        assertThat(deleted.getDeleted()).isEqualTo(1);
        assertThat(deleted.getDeletedTime()).isNotNull();

        JsonNode trashBody = readBody(mockMvc.perform(get(API_PREFIX + "/note/trash")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(trashBody.path("data")).hasSize(1);
        assertThat(trashBody.path("data").get(0).path("id").asLong()).isEqualTo(noteId);

        mockMvc.perform(post(API_PREFIX + "/note/{id}/trash/restore", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());
        assertThat(noteMapper.selectById(noteId).getDeleted()).isEqualTo(0);

        mockMvc.perform(delete(API_PREFIX + "/note/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(API_PREFIX + "/note/{id}/trash/permanent", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());
        assertThat(noteMapper.selectById(noteId)).isNull();
    }

    @Test
    void exportingNoteShouldPreserveChineseFilenameAndContent() throws Exception {
        insertUser("exporter", "password123", "USER");
        String token = login("exporter", "password123");
        long noteId = createNote(token, "灵感计划", List.of("中文", "导出"));

        MvcResult markdownResult = mockMvc.perform(post(API_PREFIX + "/note/export/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .param("format", "MARKDOWN"))
                .andExpect(status().isOk())
                .andReturn();
        byte[] markdownBytes = markdownResult.getResponse().getContentAsByteArray();

        assertThat(markdownResult.getResponse().getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("UTF-8");
        assertThat(markdownResult.getResponse().getContentType()).contains("text/markdown");
        assertThat(markdownBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(markdownBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(markdownBytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(markdownBytes, StandardCharsets.UTF_8)).contains("# 灵感计划");

        MvcResult wordResult = mockMvc.perform(post(API_PREFIX + "/note/export/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .param("format", "WORD"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(wordResult.getResponse().getContentType()).contains("charset=UTF-8");
        assertThat(new String(wordResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("Content-Type")
                .contains("灵感计划");

        MvcResult pdfResult = mockMvc.perform(post(API_PREFIX + "/note/export/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .param("format", "PDF"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(new String(pdfResult.getResponse().getContentAsByteArray(), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
    }

    @Test
    void deletingFolderShouldDetachTrashedNotesBeforeRemovingFolder() throws Exception {
        insertUser("folder-owner", "password123", "USER");
        String token = login("folder-owner", "password123");

        long folderId = createFolder(token, "Archive");
        long noteId = createNote(token, "Archived Note", List.of("trash"), folderId);

        mockMvc.perform(delete(API_PREFIX + "/note/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API_PREFIX + "/folder/{id}", folderId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_folder WHERE id = ?",
                Long.class,
                folderId
        )).isZero();

        Note trashedNote = noteMapper.selectById(noteId);
        assertThat(trashedNote).isNotNull();
        assertThat(trashedNote.getDeleted()).isEqualTo(1);
        assertThat(trashedNote.getFolderId()).isNull();
    }

    @Test
    void graphAndDailyInspirationEndpointsShouldUseRealDatabaseData() throws Exception {
        insertUser("graph-user", "password123", "USER");
        String token = login("graph-user", "password123");

        long sourceNoteId = createNote(token, "Source Note", List.of("java", "graph"));
        long targetNoteId = createNote(token, "Target Note", List.of("graph", "search"));

        mockMvc.perform(post(API_PREFIX + "/graph/relation")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceNoteId", sourceNoteId,
                                "targetNoteId", targetNoteId,
                                "relationType", "相关"
                        ))))
                .andExpect(status().isOk());

        JsonNode graphBody = readBody(mockMvc.perform(get(API_PREFIX + "/graph/global")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(graphBody.path("code").asInt()).isEqualTo(200);
        assertThat(graphBody.path("data").path("nodes")).hasSize(2);
        assertThat(graphBody.path("data").path("links")).hasSize(1);

        JsonNode inspirationBody = readBody(mockMvc.perform(get(API_PREFIX + "/daily-inspiration")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(inspirationBody.path("code").asInt()).isEqualTo(200);
        assertThat(inspirationBody.path("data").path("recommendedTags").toString()).contains("graph");
        assertThat(inspirationBody.path("data").path("recommendations").size()).isGreaterThan(0);
        assertThat(inspirationBody.path("data").path("recommendations").toString()).contains("graph");
        assertThat(inspirationBody.path("data").path("inspirationPrompts").size()).isGreaterThan(0);
        assertThat(inspirationBody.path("data").path("inspirationPrompts").toString()).contains("graph");
        assertThat(inspirationBody.path("data").path("matchSummary").asText()).contains("匹配");
    }

    @Test
    void collaboratorSearchAndOwnerPermissionShouldStayAligned() throws Exception {
        User owner = insertUser("owner-user", "password123", "USER");
        User ownerLevelCollaborator = insertUser("pair-helper", "password123", "USER");
        User reader = insertUser("pair-reader", "password123", "USER");

        String ownerToken = login("owner-user", "password123");
        long noteId = createNote(ownerToken, "Pairing Note", List.of("collab"));

        mockMvc.perform(post(API_PREFIX + "/collaborator/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", ownerLevelCollaborator.getId(),
                                "permission", "OWNER"
                        ))))
                .andExpect(status().isOk());

        JsonNode collaboratorsBody = readBody(mockMvc.perform(get(API_PREFIX + "/collaborator/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(collaboratorsBody.path("data")).hasSize(1);
        assertThat(collaboratorsBody.path("data").get(0).path("username").asText()).isEqualTo("pair-helper");
        assertThat(collaboratorsBody.path("data").get(0).path("nickname").asText()).isEqualTo("pair-helper");
        assertThat(collaboratorsBody.path("data").get(0).path("email").asText()).isEqualTo("pair-helper@example.com");

        JsonNode searchBody = readBody(mockMvc.perform(get(API_PREFIX + "/user/search")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("keyword", "pair"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(searchBody.path("data").size()).isEqualTo(2);
        assertThat(searchBody.path("data").toString()).contains("pair-helper");
        assertThat(searchBody.path("data").toString()).contains("pair-reader");
        assertThat(searchBody.path("data").toString()).doesNotContain("owner-user");

        String collaboratorToken = login("pair-helper", "password123");

        JsonNode noteBody = readBody(mockMvc.perform(get(API_PREFIX + "/note/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(collaboratorToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(noteBody.path("data").path("ownerUserId").asLong()).isEqualTo(owner.getId());
        assertThat(noteBody.path("data").path("ownerUsername").asText()).isEqualTo("owner-user");
        assertThat(noteBody.path("data").path("currentUserPermission").asText()).isEqualTo("OWNER");
        assertThat(noteBody.path("data").path("currentUserCanManage").asBoolean()).isTrue();

        mockMvc.perform(post(API_PREFIX + "/collaborator/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(collaboratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", reader.getId(),
                                "permission", "READ"
                        ))))
                .andExpect(status().isOk());

        JsonNode updatedCollaboratorsBody = readBody(mockMvc.perform(get(API_PREFIX + "/collaborator/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updatedCollaboratorsBody.path("data")).hasSize(2);
        assertThat(updatedCollaboratorsBody.path("data").toString()).contains("pair-helper");
        assertThat(updatedCollaboratorsBody.path("data").toString()).contains("pair-reader");
    }

    @Test
    void notificationCenterShouldCollectShareCollaborationCommentAndPermissionChanges() throws Exception {
        User owner = insertUser("notify-owner", "password123", "USER");
        User helper = insertUser("notify-helper", "password123", "USER");

        String ownerToken = login("notify-owner", "password123");
        long noteId = createNote(ownerToken, "Notification Note", List.of("notify", "collab"));

        mockMvc.perform(post(API_PREFIX + "/collaborator/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", helper.getId(),
                                "permission", "EDIT"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API_PREFIX + "/share/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("isPublic", 1))))
                .andExpect(status().isOk());

        String helperToken = login("notify-helper", "password123");

        JsonNode commentBody = readBody(mockMvc.perform(post(API_PREFIX + "/comment/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(helperToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Please review this plan."))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(commentBody.path("data").path("content").asText()).contains("review");

        mockMvc.perform(put(API_PREFIX + "/collaborator/{noteId}/{userId}", noteId, helper.getId())
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("permission", "READ"))
                .andExpect(status().isOk());

        JsonNode helperInbox = readBody(mockMvc.perform(get(API_PREFIX + "/notification")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(helperToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(helperInbox.path("data")).hasSize(3);
        assertThat(helperInbox.path("data").toString()).contains("COLLABORATION_INVITE");
        assertThat(helperInbox.path("data").toString()).contains("SHARE_CREATED");
        assertThat(helperInbox.path("data").toString()).contains("PERMISSION_CHANGED");

        JsonNode ownerInbox = readBody(mockMvc.perform(get(API_PREFIX + "/notification")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerInbox.path("data")).hasSize(1);
        assertThat(ownerInbox.path("data").get(0).path("type").asText()).isEqualTo("COMMENT");
        assertThat(ownerInbox.path("data").get(0).path("actorUsername").asText()).isEqualTo("notify-helper");

        long firstNotificationId = helperInbox.path("data").get(0).path("id").asLong();
        mockMvc.perform(post(API_PREFIX + "/notification/{id}/read", firstNotificationId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(helperToken)))
                .andExpect(status().isOk());

        JsonNode countBody = readBody(mockMvc.perform(get(API_PREFIX + "/notification/unread-count")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(helperToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(countBody.path("data").path("count").asLong()).isEqualTo(2L);

        mockMvc.perform(post(API_PREFIX + "/notification/read-all")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(helperToken)))
                .andExpect(status().isOk());

        JsonNode clearedCountBody = readBody(mockMvc.perform(get(API_PREFIX + "/notification/unread-count")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(helperToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(clearedCountBody.path("data").path("count").asLong()).isZero();
    }

    @Test
    void shareLinkShouldUseConfiguredPublicAppUrl() throws Exception {
        insertUser("share-owner", "password123", "USER");
        String token = login("share-owner", "password123");
        long noteId = createNote(token, "Public Share Note", List.of("share"));

        JsonNode shareBody = readBody(mockMvc.perform(post(API_PREFIX + "/share/{noteId}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("isPublic", 1))))
                .andExpect(status().isOk())
                .andReturn());

        String shareLink = shareBody.path("data").asText();
        assertThat(shareLink).startsWith("https://knowledgepulse.example.test/share/");

        String shareToken = shareLink.substring(shareLink.lastIndexOf('/') + 1);
        JsonNode publicBody = readBody(mockMvc.perform(get(API_PREFIX + "/share/public/{token}", shareToken)
                        .contextPath(API_PREFIX))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(publicBody.path("data").path("title").asText()).isEqualTo("Public Share Note");
    }

    @Test
    void attachmentCenterShouldTrackUnusedAndReferencedFiles() throws Exception {
        insertUser("attachment-owner", "password123", "USER");
        String token = login("attachment-owner", "password123");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "design.pdf",
                "application/pdf",
                "%PDF-1.4 attachment".getBytes()
        );

        JsonNode uploadBody = readBody(mockMvc.perform(multipart(API_PREFIX + "/attachment/upload")
                        .file(file)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andReturn());
        long attachmentId = uploadBody.path("data").path("id").asLong();
        String fileUrl = uploadBody.path("data").path("fileUrl").asText();
        assertThat(uploadBody.path("data").path("fileType").asText()).isEqualTo("PDF");

        JsonNode unusedBeforeBody = readBody(mockMvc.perform(get(API_PREFIX + "/attachment")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .param("unusedOnly", "true"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unusedBeforeBody.path("data")).hasSize(1);

        Map<String, Object> payload = notePayload(
                "Attachment Note",
                "[design.pdf](" + fileUrl + "?attachmentId=" + attachmentId + ")",
                "<p><a href=\"" + fileUrl + "?attachmentId=" + attachmentId + "\">design.pdf</a></p>",
                List.of("attachment"),
                null
        );
        payload.put("attachmentIds", List.of(attachmentId));

        JsonNode noteBody = readBody(mockMvc.perform(post(API_PREFIX + "/note")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn());
        long noteId = noteBody.path("data").path("id").asLong();

        JsonNode unusedAfterBody = readBody(mockMvc.perform(get(API_PREFIX + "/attachment")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .param("unusedOnly", "true"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unusedAfterBody.path("data")).hasSize(0);

        JsonNode allBody = readBody(mockMvc.perform(get(API_PREFIX + "/attachment")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(allBody.path("data").get(0).path("used").asBoolean()).isTrue();
        assertThat(allBody.path("data").get(0).path("referenceCount").asLong()).isEqualTo(1L);

        JsonNode deleteUsedBody = readBody(mockMvc.perform(delete(API_PREFIX + "/attachment/{id}", attachmentId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(deleteUsedBody.path("code").asInt()).isEqualTo(400);

        mockMvc.perform(delete(API_PREFIX + "/note/{id}", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(API_PREFIX + "/note/{id}/trash/permanent", noteId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(API_PREFIX + "/attachment/{id}", attachmentId)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note_attachment", Long.class)).isZero();
    }

    @Test
    void attachmentReferencesShouldNotBeCreatedForAnotherUsersNewAttachment() throws Exception {
        insertUser("attachment-source", "password123", "USER");
        insertUser("attachment-stranger", "password123", "USER");
        String sourceToken = login("attachment-source", "password123");
        String strangerToken = login("attachment-stranger", "password123");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "private-plan.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "private plan".getBytes()
        );

        JsonNode uploadBody = readBody(mockMvc.perform(multipart(API_PREFIX + "/attachment/upload")
                        .file(file)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(sourceToken)))
                .andExpect(status().isOk())
                .andReturn());
        long attachmentId = uploadBody.path("data").path("id").asLong();
        String fileUrl = uploadBody.path("data").path("fileUrl").asText();

        Map<String, Object> payload = notePayload(
                "Foreign Attachment Note",
                "[private-plan.docx](" + fileUrl + "?attachmentId=" + attachmentId + ")",
                "<p><a href=\"" + fileUrl + "?attachmentId=" + attachmentId + "\">private-plan.docx</a></p>",
                List.of("attachment"),
                null
        );
        payload.put("attachmentIds", List.of(attachmentId));

        mockMvc.perform(post(API_PREFIX + "/note")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(strangerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_attachment_reference WHERE attachment_id=?",
                Long.class,
                attachmentId
        )).isZero();

        JsonNode unusedBody = readBody(mockMvc.perform(get(API_PREFIX + "/attachment")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(sourceToken))
                        .param("unusedOnly", "true"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unusedBody.path("data")).hasSize(1);
    }

    @Test
    void markdownImportShouldPreserveVaultFoldersTagsAndVersions() throws Exception {
        insertUser("import-owner", "password123", "USER");
        String token = login("import-owner", "password123");

        MockMultipartFile roadmap = new MockMultipartFile(
                "files",
                "Roadmap.md",
                "text/markdown",
                """
                ---
                tags:
                  - 产品
                  - roadmap
                ---
                # 知识库路线图

                下一阶段整理 #计划/二期 和导入体验。
                """.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile daily = new MockMultipartFile(
                "files",
                "2026-04-24.md",
                "text/markdown",
                """
                今天把 Obsidian vault 带进来。

                #复盘 #导入
                """.getBytes(StandardCharsets.UTF_8)
        );

        JsonNode importBody = readBody(mockMvc.perform(multipart(API_PREFIX + "/import/markdown")
                        .file(roadmap)
                        .file(daily)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .param("mode", "OBSIDIAN_VAULT")
                        .param("rootFolderName", "我的 Obsidian 库")
                        .param("paths", "Vault/Projects/Roadmap.md", "Vault/Daily/2026-04-24.md"))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode data = importBody.path("data");
        assertThat(data.path("rootFolderName").asText()).isEqualTo("我的 Obsidian 库");
        assertThat(data.path("importedNotes").asInt()).isEqualTo(2);
        assertThat(data.path("createdFolders").asInt()).isEqualTo(3);
        assertThat(data.path("skippedFiles").asInt()).isZero();
        assertThat(data.path("tags").toString()).contains("产品", "roadmap", "计划/二期", "复盘", "导入");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note_folder", Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note_version", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note_tag", Long.class)).isEqualTo(5L);

        String roadmapContent = jdbcTemplate.queryForObject(
                "SELECT content FROM note WHERE title = ?",
                String.class,
                "知识库路线图"
        );
        assertThat(roadmapContent).doesNotContain("tags:");
        assertThat(roadmapContent).contains("#计划/二期");
    }

    @Test
    void documentImportShouldExtractPdfAndWordIntoNotesWithSourceAttachments() throws Exception {
        insertUser("document-owner", "password123", "USER");
        String token = login("document-owner", "password123");

        MockMultipartFile pdf = pdfFile("Research.pdf", "Quarterly Research Notes");
        MockMultipartFile docx = docxFile("客户访谈.docx", "客户访谈记录", "用户希望把资料直接转成笔记。");

        JsonNode importBody = readBody(mockMvc.perform(multipart(API_PREFIX + "/import/documents")
                        .file(pdf)
                        .file(docx)
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .param("rootFolderName", "文档资料")
                        .param("keepAttachments", "true")
                        .param("paths", "Docs/Research.pdf", "Docs/客户访谈.docx"))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode data = importBody.path("data");
        assertThat(data.path("mode").asText()).isEqualTo("DOCUMENT_EXTRACT");
        assertThat(data.path("importedNotes").asInt()).isEqualTo(2);
        assertThat(data.path("attachments").size()).isEqualTo(2);
        assertThat(data.path("tags").toString()).contains("文档导入", "PDF", "Word");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note_attachment", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM note_attachment_reference", Long.class)).isEqualTo(2L);

        String wordContent = jdbcTemplate.queryForObject(
                "SELECT content FROM note WHERE title = ?",
                String.class,
                "客户访谈"
        );
        assertThat(wordContent).contains("客户访谈记录", "来源附件", "用户希望把资料直接转成笔记");
    }

    @Test
    void adminAndAuditorPermissionsShouldBeSeparated() throws Exception {
        User admin = insertUser("admin", "password123", "ADMIN");
        User auditUser = insertUser("auditor", "password123", "USER");

        String adminToken = login("admin", "password123");

        JsonNode roleBody = readBody(mockMvc.perform(put(API_PREFIX + "/admin/users/{id}/role", auditUser.getId())
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "AUDITOR"))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(roleBody.path("code").asInt()).isEqualTo(200);
        assertThat(roleBody.path("data").path("role").asText()).isEqualTo("AUDITOR");
        assertThat(userMapper.selectById(auditUser.getId()).getRole()).isEqualTo("AUDITOR");

        String auditorToken = login("auditor", "password123");

        JsonNode logBody = readBody(mockMvc.perform(get(API_PREFIX + "/admin/logs")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(auditorToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(logBody.path("code").asInt()).isEqualTo(200);
        assertThat(logBody.path("data").size()).isGreaterThan(0);

        JsonNode forbiddenBody = readBody(mockMvc.perform(put(API_PREFIX + "/admin/users/{id}/role", admin.getId())
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(forbiddenBody.path("code").asInt()).isEqualTo(403);

        JsonNode warmupBody = readBody(mockMvc.perform(post(API_PREFIX + "/admin/jobs/daily-inspiration/warmup")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(adminToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(warmupBody.path("code").asInt()).isEqualTo(200);

        JsonNode mineBody = readBody(mockMvc.perform(get(API_PREFIX + "/operation-log/mine")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(auditorToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(mineBody.path("code").asInt()).isEqualTo(200);
        assertThat(mineBody.path("data").size()).isGreaterThan(0);
    }

    private User insertUser(String username, String rawPassword, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmail(username + "@example.com");
        user.setNickname(username);
        user.setRole(role);
        userMapper.insert(user);
        return user;
    }

    private String login(String username, String password) throws Exception {
        JsonNode body = readBody(mockMvc.perform(post(API_PREFIX + "/user/login")
                        .contextPath(API_PREFIX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        return body.path("data").path("accessToken").asText();
    }

    private long createNote(String token, String title, List<String> tags) throws Exception {
        return createNote(token, title, tags, null);
    }

    private long createNote(String token, String title, List<String> tags, Long folderId) throws Exception {
        JsonNode body = readBody(mockMvc.perform(post(API_PREFIX + "/note")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notePayload(
                                title,
                                "# " + title,
                                "<h1>" + title + "</h1>",
                                tags,
                                folderId
                        ))))
                .andExpect(status().isOk())
                .andReturn());
        return body.path("data").path("id").asLong();
    }

    private long createFolder(String token, String name) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("parentId", null);

        JsonNode body = readBody(mockMvc.perform(post(API_PREFIX + "/folder")
                        .contextPath(API_PREFIX)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn());
        return body.path("data").path("id").asLong();
    }

    private Map<String, Object> notePayload(String title, String content, String htmlContent, List<String> tags, Long folderId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("content", content);
        payload.put("htmlContent", htmlContent);
        payload.put("tags", tags);
        payload.put("folderId", folderId);
        return payload;
    }

    private MockMultipartFile docxFile(String fileName, String title, String content) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(title);
            document.createParagraph().createRun().setText(content);
            document.write(outputStream);
            return new MockMultipartFile(
                    "files",
                    fileName,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    outputStream.toByteArray()
            );
        }
    }

    private MockMultipartFile pdfFile(String fileName, String content) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(48, 720);
                contentStream.showText(content);
                contentStream.endText();
            }
            document.save(outputStream);
            return new MockMultipartFile("files", fileName, "application/pdf", outputStream.toByteArray());
        }
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }
}
