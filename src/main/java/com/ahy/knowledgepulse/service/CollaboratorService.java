package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.CollaboratorRequest;
import com.ahy.knowledgepulse.dto.response.CollaboratorResponse;

import java.util.List;

public interface CollaboratorService {

    void addCollaborator(Long noteId, CollaboratorRequest request);

    void removeCollaborator(Long noteId, Long userId);

    void updatePermission(Long noteId, Long userId, String permission);

    List<CollaboratorResponse> getCollaborators(Long noteId);

    String getPermission(Long noteId, Long userId);
}
