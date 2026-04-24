package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.ShareRequest;
import com.ahy.knowledgepulse.dto.response.NoteResponse;

public interface ShareService {

    String generateShareLink(Long noteId, ShareRequest request);

    NoteResponse getSharedNote(String token, String password);

    void revokeShare(Long noteId);
}
