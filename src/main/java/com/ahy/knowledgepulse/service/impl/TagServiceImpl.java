package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteTag;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteTagMapper;
import com.ahy.knowledgepulse.service.NotePermissionService;
import com.ahy.knowledgepulse.service.TagService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final NoteTagMapper tagMapper;
    private final NoteMapper noteMapper;
    private final NotePermissionService notePermissionService;

    @Override
    public List<String> getAllTags() {
        Long userId = SecurityUtil.getCurrentUserId();
        return tagMapper.findAllTagsByUserId(userId);
    }

    @Override
    public List<String> getTopTags() {
        Long userId = SecurityUtil.getCurrentUserId();
        return tagMapper.findTopTagsByUserId(userId);
    }

    @Override
    @Transactional
    public void addTagsToNote(Long noteId, List<String> tags) {
        Note note = requireEditableNote(noteId);
        persistTags(note, tags);
    }

    @Override
    @Transactional
    public void removeTagFromNote(Long noteId, String tagName) {
        Note note = requireEditableNote(noteId);
        List<String> currentTags = tagMapper.findTagsByNoteId(noteId).stream()
                .filter(existing -> !existing.equalsIgnoreCase(tagName))
                .toList();
        persistTags(note, currentTags);
    }

    @Override
    @Transactional
    public void updateNoteTags(Long noteId, List<String> tags) {
        Note note = requireEditableNote(noteId);
        persistTags(note, tags);
    }

    private Note requireEditableNote(Long noteId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canEdit(note, userId)) {
            throw new BusinessException(403, "No permission to edit note tags");
        }
        return note;
    }

    private void persistTags(Note note, List<String> tags) {
        tagMapper.deleteByNoteId(note.getId());

        List<String> normalizedTags = tags == null
                ? List.of()
                : tags.stream()
                        .map(tag -> tag == null ? "" : tag.trim())
                        .filter(tag -> !tag.isEmpty())
                        .collect(java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                List::copyOf
                        ));

        for (String tag : normalizedTags) {
            NoteTag noteTag = new NoteTag();
            noteTag.setNoteId(note.getId());
            noteTag.setTagName(tag);
            tagMapper.insert(noteTag);
        }

        note.setTags(String.join(",", normalizedTags));
        noteMapper.updateById(note);
    }
}
