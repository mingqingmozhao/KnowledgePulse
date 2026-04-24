package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.request.NoteTemplateRequest;
import com.ahy.knowledgepulse.dto.response.NoteTemplateResponse;

import java.util.List;

public interface NoteTemplateService {

    List<NoteTemplateResponse> getTemplates();

    NoteTemplateResponse getTemplate(Long id);

    NoteTemplateResponse createTemplate(NoteTemplateRequest request);

    NoteTemplateResponse updateTemplate(Long id, NoteTemplateRequest request);

    void deleteTemplate(Long id);
}
