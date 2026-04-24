package com.ahy.knowledgepulse.controller;

import com.ahy.knowledgepulse.dto.request.NoteRequest;
import com.ahy.knowledgepulse.dto.response.ExportPayload;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.dto.response.Result;
import com.ahy.knowledgepulse.dto.response.VersionResponse;
import com.ahy.knowledgepulse.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
@Tag(name = "Note", description = "Note CRUD, version history, trash and export")
public class NoteController {

    private final NoteService noteService;

    @GetMapping("/list")
    @Operation(summary = "Get note list", description = "Fetch all accessible notes or notes under a folder")
    public Result<List<NoteResponse>> getNotes(@RequestParam(required = false) Long folderId) {
        if (folderId != null) {
            return Result.success(noteService.getNotesByFolder(folderId));
        }
        return Result.success(noteService.getAllNotes());
    }

    @GetMapping("/favorites")
    @Operation(summary = "Get favorite notes", description = "Fetch all favorited accessible notes")
    public Result<List<NoteResponse>> getFavoriteNotes() {
        return Result.success(noteService.getFavoriteNotes());
    }

    @GetMapping("/daily")
    @Operation(summary = "Get or create daily note", description = "Fetch the current user's daily note for a date or create it when absent")
    public Result<NoteResponse> getOrCreateDailyNote(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return Result.success(noteService.getOrCreateDailyNote(date));
    }

    @GetMapping("/daily/calendar")
    @Operation(summary = "Get daily note calendar markers", description = "Fetch dates that already have daily notes in a month")
    public Result<List<String>> getDailyNoteCalendar(@RequestParam String month) {
        YearMonth targetMonth = YearMonth.parse(month);
        return Result.success(noteService.getDailyNoteDates(targetMonth.atDay(1), targetMonth.atEndOfMonth())
                .stream()
                .map(LocalDate::toString)
                .toList());
    }

    @GetMapping("/trash")
    @Operation(summary = "Get trash notes", description = "Fetch notes in recycle bin")
    public Result<List<NoteResponse>> getTrashNotes() {
        return Result.success(noteService.getTrashNotes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get note detail", description = "Fetch a note by id")
    public Result<NoteResponse> getNoteById(@PathVariable Long id) {
        return Result.success(noteService.getNoteById(id));
    }

    @PostMapping
    @Operation(summary = "Create note", description = "Create a new note")
    public Result<NoteResponse> createNote(@Valid @RequestBody NoteRequest request) {
        return Result.success(noteService.createNote(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update note", description = "Update note content and save a new version")
    public Result<NoteResponse> updateNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return Result.success(noteService.updateNote(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Move note to trash", description = "Soft delete a note into recycle bin")
    public Result<Void> deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "Favorite note", description = "Favorite an accessible note")
    public Result<NoteResponse> favoriteNote(@PathVariable Long id) {
        return Result.success(noteService.favoriteNote(id));
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "Unfavorite note", description = "Remove a note from favorites")
    public Result<NoteResponse> unfavoriteNote(@PathVariable Long id) {
        return Result.success(noteService.unfavoriteNote(id));
    }

    @DeleteMapping("/{id}/trash/permanent")
    @Operation(summary = "Permanently delete note", description = "Delete a trashed note permanently")
    public Result<Void> permanentlyDeleteNote(@PathVariable Long id) {
        noteService.permanentlyDeleteNote(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/trash/restore")
    @Operation(summary = "Restore note from trash", description = "Restore a trashed note")
    public Result<NoteResponse> restoreFromTrash(@PathVariable Long id) {
        return Result.success(noteService.restoreFromTrash(id));
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "Get version history", description = "Fetch note versions")
    public Result<List<VersionResponse>> getNoteVersions(@PathVariable Long id) {
        return Result.success(noteService.getNoteVersions(id));
    }

    @PostMapping("/{id}/restore/{version}")
    @Operation(summary = "Restore version", description = "Restore note content to a historical version")
    public Result<NoteResponse> restoreVersion(@PathVariable Long id, @PathVariable Integer version) {
        return Result.success(noteService.restoreVersion(id, version));
    }

    @PostMapping("/export/{id}")
    @Operation(summary = "Export note", description = "Export note as PDF, Markdown or Word")
    public ResponseEntity<ByteArrayResource> exportNote(
            @PathVariable Long id,
            @RequestParam(defaultValue = "MARKDOWN") String format
    ) {
        ExportPayload payload = noteService.exportNote(id, format);
        MediaType mediaType = MediaType.parseMediaType(payload.getContentType());
        String encodedFileName = new String(payload.getFileName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .body(new ByteArrayResource(payload.getContent()));
    }
}
