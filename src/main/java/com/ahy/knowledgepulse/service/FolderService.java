package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.FolderRequest;
import com.ahy.knowledgepulse.dto.response.FolderResponse;

import java.util.List;

public interface FolderService {

    FolderResponse createFolder(FolderRequest request);

    FolderResponse updateFolder(Long id, FolderRequest request);

    void deleteFolder(Long id);

    FolderResponse getFolderById(Long id);

    List<FolderResponse> getFolderTree();
}
