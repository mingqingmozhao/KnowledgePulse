package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.response.AttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

public interface NoteAttachmentService {

    AttachmentResponse upload(MultipartFile file);

    List<AttachmentResponse> list(String fileType, Boolean unusedOnly, String keyword);

    void deleteAttachment(Long attachmentId);

    void syncReferences(Long noteId, Collection<Long> attachmentIds, String content, String htmlContent);
}
