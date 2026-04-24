package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.RelationRequest;
import com.ahy.knowledgepulse.dto.response.GraphData;
import com.ahy.knowledgepulse.dto.response.GraphLink;
import com.ahy.knowledgepulse.dto.response.GraphNode;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteRelation;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteRelationMapper;
import com.ahy.knowledgepulse.service.GraphService;
import com.ahy.knowledgepulse.service.NotePermissionService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private final NoteRelationMapper relationMapper;
    private final NoteMapper noteMapper;
    private final NotePermissionService notePermissionService;
    private final OperationLogService operationLogService;

    @Override
    public GraphData getGraphData(Long noteId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note centerNote = requireReadableNote(noteId, userId);

        List<NoteRelation> relations = relationMapper.findBySourceNoteId(noteId);
        relations.addAll(relationMapper.findByTargetNoteId(noteId));

        Set<Long> noteIds = new LinkedHashSet<>();
        noteIds.add(noteId);
        relations.forEach(relation -> {
            noteIds.add(relation.getSourceNoteId());
            noteIds.add(relation.getTargetNoteId());
        });

        List<Note> accessibleNotes = noteIds.stream()
                .map(noteMapper::selectById)
                .filter(note -> note != null && (note.getDeleted() == null || note.getDeleted() == 0))
                .filter(note -> notePermissionService.canRead(note, userId))
                .toList();

        Set<Long> accessibleNoteIds = accessibleNotes.stream()
                .map(Note::getId)
                .collect(Collectors.toSet());

        GraphData graphData = new GraphData();
        for (Note note : accessibleNotes) {
            String nodeType = note.getId().equals(centerNote.getId()) ? "CENTER" : "RELATED";
            graphData.getNodes().add(new GraphNode(note.getId(), note.getTitle(), nodeType));
        }

        for (NoteRelation relation : relations) {
            if (accessibleNoteIds.contains(relation.getSourceNoteId())
                    && accessibleNoteIds.contains(relation.getTargetNoteId())) {
                graphData.getLinks().add(new GraphLink(
                        relation.getSourceNoteId(),
                        relation.getTargetNoteId(),
                        relation.getRelationType()
                ));
            }
        }

        return graphData;
    }

    @Override
    public GraphData getGlobalGraph() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<Note> accessibleNotes = noteMapper.findAccessibleNotes(userId);
        List<Long> accessibleNoteIds = accessibleNotes.stream().map(Note::getId).toList();
        List<NoteRelation> relations = accessibleNoteIds.isEmpty()
                ? List.of()
                : relationMapper.findRelationsByNoteIds(accessibleNoteIds);

        GraphData graphData = new GraphData();
        accessibleNotes.forEach(note -> graphData.getNodes().add(new GraphNode(note.getId(), note.getTitle(), "NOTE")));
        relations.forEach(relation -> graphData.getLinks().add(new GraphLink(
                relation.getSourceNoteId(),
                relation.getTargetNoteId(),
                relation.getRelationType()
        )));
        return graphData;
    }

    @Override
    @Transactional
    public void addRelation(RelationRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Note sourceNote = requireReadableNote(request.getSourceNoteId(), userId);
        Note targetNote = requireReadableNote(request.getTargetNoteId(), userId);

        if (!notePermissionService.canEdit(sourceNote, userId) || !notePermissionService.canEdit(targetNote, userId)) {
            throw new BusinessException(403, "No permission to create relation for these notes");
        }
        if (request.getSourceNoteId().equals(request.getTargetNoteId())) {
            throw new BusinessException(400, "A note cannot relate to itself");
        }

        NoteRelation relation = new NoteRelation();
        relation.setSourceNoteId(request.getSourceNoteId());
        relation.setTargetNoteId(request.getTargetNoteId());
        relation.setRelationType(request.getRelationType());
        relationMapper.insert(relation);
        operationLogService.record(userId, "GRAPH",
                "Added relation " + request.getRelationType()
                        + " between note#" + request.getSourceNoteId()
                        + " and note#" + request.getTargetNoteId());
    }

    @Override
    @Transactional
    public void deleteRelation(Long id) {
        NoteRelation relation = relationMapper.selectById(id);
        if (relation == null) {
            return;
        }

        Long userId = SecurityUtil.getCurrentUserId();
        Note sourceNote = requireReadableNote(relation.getSourceNoteId(), userId);
        if (!notePermissionService.canEdit(sourceNote, userId)) {
            throw new BusinessException(403, "No permission to delete this relation");
        }
        relationMapper.deleteById(id);
        operationLogService.record(userId, "GRAPH", "Deleted relation #" + id);
    }

    private Note requireReadableNote(Long noteId, Long userId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getDeleted() != null && note.getDeleted() == 1)) {
            throw new BusinessException(404, "Note does not exist");
        }
        if (!notePermissionService.canRead(note, userId)) {
            throw new BusinessException(403, "No permission to access this note");
        }
        return note;
    }
}
