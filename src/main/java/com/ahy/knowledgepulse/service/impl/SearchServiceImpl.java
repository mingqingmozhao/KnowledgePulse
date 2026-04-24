package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.SearchResult;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.service.SearchService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final NoteMapper noteMapper;

    @Override
    public List<SearchResult> search(String keyword) {
        Long userId = SecurityUtil.getCurrentUserId();
        return noteMapper.searchAccessibleNotes(userId, keyword)
                .stream()
                .map(note -> convertToResult(note, keyword))
                .toList();
    }

    @Override
    public List<SearchResult> searchByTag(String tagName) {
        Long userId = SecurityUtil.getCurrentUserId();
        return noteMapper.findAccessibleNotesWithTag(userId, tagName)
                .stream()
                .map(note -> convertToResult(note, tagName))
                .toList();
    }

    private SearchResult convertToResult(Note note, String keyword) {
        SearchResult result = new SearchResult();
        result.setId(note.getId());
        result.setTitle(note.getTitle());
        result.setSnippet(buildSnippet(note.getContent(), keyword, 140));
        result.setTags(note.getTags());
        result.setUpdateTime(note.getUpdateTime());
        return result;
    }

    private String buildSnippet(String content, String keyword, int maxLength) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String normalized = content.replaceAll("\\s+", " ").trim();
        if (keyword == null || keyword.isBlank()) {
            return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
        }

        int index = normalized.toLowerCase().indexOf(keyword.toLowerCase());
        if (index < 0) {
            return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
        }

        int start = Math.max(0, index - 30);
        int end = Math.min(normalized.length(), start + maxLength);
        String snippet = normalized.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < normalized.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }
}
