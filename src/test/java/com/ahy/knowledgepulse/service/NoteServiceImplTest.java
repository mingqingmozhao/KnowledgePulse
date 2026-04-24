package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.NoteRequest;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteFavorite;
import com.ahy.knowledgepulse.entity.NoteFolder;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.mapper.NoteCollaboratorMapper;
import com.ahy.knowledgepulse.mapper.NoteFavoriteMapper;
import com.ahy.knowledgepulse.mapper.NoteFolderMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteRelationMapper;
import com.ahy.knowledgepulse.mapper.NoteTagMapper;
import com.ahy.knowledgepulse.mapper.NoteVersionMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.impl.NoteServiceImpl;
import com.ahy.knowledgepulse.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private NoteVersionMapper versionMapper;

    @Mock
    private NoteFolderMapper folderMapper;

    @Mock
    private NoteFavoriteMapper favoriteMapper;

    @Mock
    private NoteTagMapper noteTagMapper;

    @Mock
    private NoteRelationMapper relationMapper;

    @Mock
    private NoteCollaboratorMapper collaboratorMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TagService tagService;

    @Mock
    private NoteAttachmentService attachmentService;

    @Mock
    private NotePermissionService notePermissionService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private NoteServiceImpl noteService;

    @Test
    void createNoteShouldPersistDefaultFieldsAndVersion() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Architecture");
        request.setContent("# content");
        request.setFolderId(11L);
        request.setTags(List.of("java", "spring"));

        NoteFolder folder = new NoteFolder();
        folder.setId(11L);
        folder.setUserId(1L);
        folder.setName("Engineering");

        User owner = new User();
        owner.setId(1L);
        owner.setUsername("writer");
        owner.setNickname("Writer");

        when(folderMapper.selectById(11L)).thenReturn(folder);
        when(userMapper.selectById(1L)).thenReturn(owner);
        when(noteTagMapper.findTagsByNoteId(100L)).thenReturn(List.of("java", "spring"));
        when(versionMapper.getLatestVersion(100L)).thenReturn(null);
        doAnswer(invocation -> {
            Note note = invocation.getArgument(0);
            note.setId(100L);
            note.setCreateTime(LocalDateTime.now());
            note.setUpdateTime(LocalDateTime.now());
            return 1;
        }).when(noteMapper).insert(any(Note.class));
        when(noteMapper.selectById(100L)).thenAnswer(invocation -> {
            Note note = new Note();
            note.setId(100L);
            note.setUserId(1L);
            note.setTitle("Architecture");
            note.setContent("# content");
            note.setHtmlContent("<pre># content</pre>");
            note.setFolderId(11L);
            note.setDeleted(0);
            note.setIsPublic(0);
            note.setCreateTime(LocalDateTime.now());
            note.setUpdateTime(LocalDateTime.now());
            return note;
        });

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            NoteResponse response = noteService.createNote(request);

            assertThat(response.getTitle()).isEqualTo("Architecture");
            assertThat(response.getFolderName()).isEqualTo("Engineering");
            assertThat(response.getTags()).containsExactly("java", "spring");
            verify(tagService).addTagsToNote(100L, List.of("java", "spring"));
            verify(versionMapper).insert(any());
        }
    }

    @Test
    void deleteNoteShouldMoveNoteIntoTrash() {
        Note note = new Note();
        note.setId(9L);
        note.setUserId(1L);
        note.setTitle("To be deleted");
        note.setDeleted(0);

        when(noteMapper.selectById(9L)).thenReturn(note);
        when(notePermissionService.canManage(note, 1L)).thenReturn(true);

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            noteService.deleteNote(9L);
        }

        ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).updateById(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getDeleted()).isEqualTo(1);
        assertThat(noteCaptor.getValue().getDeletedTime()).isNotNull();
        assertThat(noteCaptor.getValue().getIsPublic()).isEqualTo(0);
    }

    @Test
    void favoriteNoteShouldCreateFavoriteAndReturnFavoritedResponse() {
        Note note = new Note();
        note.setId(18L);
        note.setUserId(2L);
        note.setTitle("Shared note");
        note.setDeleted(0);
        note.setIsPublic(0);
        note.setUpdateTime(LocalDateTime.now());

        User owner = new User();
        owner.setId(2L);
        owner.setUsername("owner");

        NoteFavorite favorite = new NoteFavorite();
        favorite.setNoteId(18L);
        favorite.setUserId(1L);
        favorite.setCreateTime(LocalDateTime.now());

        when(noteMapper.selectById(18L)).thenReturn(note);
        when(notePermissionService.canRead(note, 1L)).thenReturn(true);
        when(notePermissionService.canManage(note, 1L)).thenReturn(false);
        when(notePermissionService.isAdmin(1L)).thenReturn(false);
        when(notePermissionService.getCollaboratorPermission(18L, 1L)).thenReturn("READ");
        when(userMapper.selectById(2L)).thenReturn(owner);
        when(noteTagMapper.findTagsByNoteId(18L)).thenReturn(List.of("shared"));
        when(favoriteMapper.findByNoteIdAndUserId(18L, 1L)).thenReturn(null, favorite);

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            NoteResponse response = noteService.favoriteNote(18L);

            assertThat(response.getFavorited()).isTrue();
            assertThat(response.getFavoriteTime()).isEqualTo(favorite.getCreateTime());
            verify(favoriteMapper).insert(any(NoteFavorite.class));
        }
    }

    @Test
    void getFavoriteNotesShouldReturnAccessibleFavoritesInFavoriteOrder() {
        Note note = new Note();
        note.setId(25L);
        note.setUserId(1L);
        note.setTitle("Owned favorite");
        note.setDeleted(0);
        note.setIsPublic(0);
        note.setUpdateTime(LocalDateTime.now());

        NoteFavorite favorite = new NoteFavorite();
        favorite.setNoteId(25L);
        favorite.setUserId(1L);
        favorite.setCreateTime(LocalDateTime.now());

        User owner = new User();
        owner.setId(1L);
        owner.setUsername("writer");

        when(favoriteMapper.findByUserId(1L)).thenReturn(List.of(favorite));
        when(noteMapper.selectById(25L)).thenReturn(note);
        when(notePermissionService.canRead(note, 1L)).thenReturn(true);
        when(notePermissionService.canManage(note, 1L)).thenReturn(true);
        when(userMapper.selectById(1L)).thenReturn(owner);
        when(noteTagMapper.findTagsByNoteId(25L)).thenReturn(List.of("java"));
        when(favoriteMapper.findByNoteIdAndUserId(25L, 1L)).thenReturn(favorite);

        try (MockedStatic<SecurityUtil> securityUtil = Mockito.mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

            List<NoteResponse> favorites = noteService.getFavoriteNotes();

            assertThat(favorites).hasSize(1);
            assertThat(favorites.get(0).getId()).isEqualTo(25L);
            assertThat(favorites.get(0).getFavorited()).isTrue();
        }
    }
}
