package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.request.NoteTemplateRequest;
import com.ahy.knowledgepulse.dto.response.NoteTemplateResponse;
import com.ahy.knowledgepulse.entity.NoteTemplate;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteTemplateMapper;
import com.ahy.knowledgepulse.service.NoteTemplateService;
import com.ahy.knowledgepulse.service.OperationLogService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteTemplateServiceImpl implements NoteTemplateService {

    private static final String DEFAULT_CATEGORY = "\u901a\u7528";

    private final NoteTemplateMapper templateMapper;
    private final OperationLogService operationLogService;

    @Override
    public List<NoteTemplateResponse> getTemplates() {
        Long userId = requireCurrentUser();
        return templateMapper.findAccessibleByUserId(userId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Override
    public NoteTemplateResponse getTemplate(Long id) {
        Long userId = requireCurrentUser();
        NoteTemplate template = templateMapper.findAccessibleById(id, userId);

        if (template == null) {
            throw new BusinessException(404, "Template does not exist");
        }

        return convertToResponse(template);
    }

    @Override
    @Transactional
    public NoteTemplateResponse createTemplate(NoteTemplateRequest request) {
        Long userId = requireCurrentUser();
        NoteTemplate template = new NoteTemplate();

        template.setUserId(userId);
        applyRequest(template, request);
        template.setSystemFlag(0);
        templateMapper.insert(template);
        operationLogService.record(userId, "TEMPLATE", "Created template #" + template.getId());
        return convertToResponse(templateMapper.selectById(template.getId()));
    }

    @Override
    @Transactional
    public NoteTemplateResponse updateTemplate(Long id, NoteTemplateRequest request) {
        Long userId = requireCurrentUser();
        NoteTemplate template = templateMapper.findOwnedById(id, userId);

        if (template == null) {
            throw new BusinessException(404, "Template does not exist or cannot be edited");
        }

        applyRequest(template, request);
        templateMapper.updateById(template);
        operationLogService.record(userId, "TEMPLATE", "Updated template #" + id);
        return convertToResponse(templateMapper.selectById(id));
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        Long userId = requireCurrentUser();
        NoteTemplate template = templateMapper.findOwnedById(id, userId);

        if (template == null) {
            throw new BusinessException(404, "Template does not exist or cannot be deleted");
        }

        templateMapper.deleteById(id);
        operationLogService.record(userId, "TEMPLATE", "Deleted template #" + id);
    }

    private void applyRequest(NoteTemplate template, NoteTemplateRequest request) {
        template.setName(request.getName().trim());
        template.setDescription(defaultText(request.getDescription()));
        template.setContent(defaultText(request.getContent()));
        template.setHtmlContent(defaultText(request.getHtmlContent()));
        template.setTags(joinTags(request.getTags()));
        template.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : DEFAULT_CATEGORY);
    }

    private Long requireCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return userId;
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }

        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptyList();
        }

        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private NoteTemplateResponse convertToResponse(NoteTemplate template) {
        NoteTemplateResponse response = new NoteTemplateResponse();
        response.setId(template.getId());
        response.setUserId(template.getUserId());
        response.setName(template.getName());
        response.setDescription(template.getDescription());
        response.setContent(template.getContent());
        response.setHtmlContent(template.getHtmlContent());
        response.setTags(splitTags(template.getTags()));
        response.setCategory(template.getCategory());
        response.setSystem(template.getSystemFlag() != null && template.getSystemFlag() == 1);
        response.setCreateTime(template.getCreateTime());
        response.setUpdateTime(template.getUpdateTime());
        return response;
    }
}
