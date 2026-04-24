package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.FolderRequest;
import com.ahy.knowledgepulse.dto.response.FolderResponse;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteFolder;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteFolderMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.service.FolderService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final NoteFolderMapper folderMapper;
    private final NoteMapper noteMapper;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public FolderResponse createFolder(FolderRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        validateParentFolder(userId, null, request.getParentId());

        NoteFolder folder = new NoteFolder();
        folder.setUserId(userId);
        folder.setName(request.getName());
        folder.setParentId(request.getParentId());
        folderMapper.insert(folder);
        operationLogService.record(userId, "FOLDER", "Created folder #" + folder.getId());
        return convertToResponse(folder);
    }

    @Override
    @Transactional
    public FolderResponse updateFolder(Long id, FolderRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        NoteFolder folder = requireOwnedFolder(id, userId);
        validateParentFolder(userId, id, request.getParentId());
        folder.setName(request.getName());
        folder.setParentId(request.getParentId());
        folderMapper.updateById(folder);
        operationLogService.record(userId, "FOLDER", "Updated folder #" + folder.getId());
        return convertToResponse(folder);
    }

    @Override
    @Transactional
    public void deleteFolder(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        requireOwnedFolder(id, userId);

        if (Integer.valueOf(0).compareTo(safeInt(folderMapper.countNotesInFolder(id))) < 0) {
            throw new BusinessException(400, "Folder is not empty");
        }
        if (Integer.valueOf(0).compareTo(safeInt(folderMapper.countChildFolders(id))) < 0) {
            throw new BusinessException(400, "Please remove child folders first");
        }

        // Trashed notes should not block folder cleanup. Once the folder is gone,
        // keep those notes in trash but detach them from the deleted folder.
        noteMapper.clearFolderForDeletedNotes(userId, id);
        folderMapper.deleteById(id);
        operationLogService.record(userId, "FOLDER", "Deleted folder #" + id);
    }

    @Override
    public FolderResponse getFolderById(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return convertToResponse(requireOwnedFolder(id, userId));
    }

    @Override
    public List<FolderResponse> getFolderTree() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<NoteFolder> folders = folderMapper.findByUserId(userId);
        List<Note> notes = noteMapper.findByUserId(userId);
        return buildTree(folders, notes, null);
    }

    private NoteFolder requireOwnedFolder(Long folderId, Long userId) {
        NoteFolder folder = folderMapper.selectById(folderId);
        if (folder == null) {
            throw new BusinessException(404, "Folder does not exist");
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BusinessException(403, "No permission to access folder");
        }
        return folder;
    }

    private void validateParentFolder(Long userId, Long currentFolderId, Long parentId) {
        if (parentId == null) {
            return;
        }

        NoteFolder parent = requireOwnedFolder(parentId, userId);
        if (currentFolderId != null && currentFolderId.equals(parent.getId())) {
            throw new BusinessException(400, "Folder cannot be its own parent");
        }

        if (currentFolderId != null && isDescendant(currentFolderId, parentId, folderMapper.findByUserId(userId))) {
            throw new BusinessException(400, "Cannot move folder into its own descendant");
        }
    }

    private boolean isDescendant(Long currentFolderId, Long parentId, List<NoteFolder> allFolders) {
        Long probeParentId = parentId;
        while (probeParentId != null) {
            if (Objects.equals(probeParentId, currentFolderId)) {
                return true;
            }

            Long currentProbeParentId = probeParentId;
            Long nextParentId = allFolders.stream()
                    .filter(folder -> Objects.equals(folder.getId(), currentProbeParentId))
                    .map(NoteFolder::getParentId)
                    .findFirst()
                    .orElse(null);
            probeParentId = nextParentId;
        }
        return false;
    }

    private Integer safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private List<FolderResponse> buildTree(List<NoteFolder> folders, List<Note> notes, Long parentId) {
        List<FolderResponse> tree = new ArrayList<>();
        for (NoteFolder folder : folders) {
            if ((parentId == null && folder.getParentId() == null)
                    || (parentId != null && parentId.equals(folder.getParentId()))) {
                FolderResponse response = convertToResponse(folder);
                response.setNotes(filterNotesByFolder(notes, folder.getId()));
                response.setChildren(buildTree(folders, notes, folder.getId()));
                tree.add(response);
            }
        }
        return tree;
    }

    private List<NoteResponse> filterNotesByFolder(List<Note> notes, Long folderId) {
        return notes.stream()
                .filter(note -> folderId.equals(note.getFolderId()))
                .map(this::convertNoteToResponse)
                .collect(Collectors.toList());
    }

    private FolderResponse convertToResponse(NoteFolder folder) {
        FolderResponse response = new FolderResponse();
        response.setId(folder.getId());
        response.setName(folder.getName());
        response.setParentId(folder.getParentId());
        response.setCreateTime(folder.getCreateTime());
        response.setChildren(new ArrayList<>());
        response.setNotes(new ArrayList<>());
        return response;
    }

    private NoteResponse convertNoteToResponse(Note note) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setTitle(note.getTitle());
        response.setContent(note.getContent());
        response.setHtmlContent(note.getHtmlContent());
        response.setFolderId(note.getFolderId());
        response.setDeleted(note.getDeleted());
        response.setDeletedTime(note.getDeletedTime());
        response.setCreateTime(note.getCreateTime());
        response.setUpdateTime(note.getUpdateTime());
        return response;
    }
}
