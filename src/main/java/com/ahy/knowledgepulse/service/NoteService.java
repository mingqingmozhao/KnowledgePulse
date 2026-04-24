package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.NoteRequest;
import com.ahy.knowledgepulse.dto.response.ExportPayload;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.dto.response.VersionResponse;

import java.time.LocalDate;
import java.util.List;

public interface NoteService {

    NoteResponse createNote(NoteRequest request);

    NoteResponse updateNote(Long id, NoteRequest request);

    void deleteNote(Long id);

    NoteResponse getNoteById(Long id);

    List<NoteResponse> getNotesByFolder(Long folderId);

    List<NoteResponse> getAllNotes();

    List<NoteResponse> getFavoriteNotes();

    NoteResponse getOrCreateDailyNote(LocalDate dailyNoteDate);

    List<LocalDate> getDailyNoteDates(LocalDate startDate, LocalDate endDate);

    List<VersionResponse> getNoteVersions(Long noteId);

    NoteResponse restoreVersion(Long noteId, Integer version);

    List<NoteResponse> getTrashNotes();

    NoteResponse restoreFromTrash(Long noteId);

    NoteResponse favoriteNote(Long noteId);

    NoteResponse unfavoriteNote(Long noteId);

    void permanentlyDeleteNote(Long noteId);

    ExportPayload exportNote(Long noteId, String format);

    void cleanupExpiredDeletedNotes();
}
