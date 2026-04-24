package com.ahy.knowledgepulse.service;

import java.util.List;

public interface TagService {

    List<String> getAllTags();

    List<String> getTopTags();

    void addTagsToNote(Long noteId, List<String> tags);

    void removeTagFromNote(Long noteId, String tagName);

    void updateNoteTags(Long noteId, List<String> tags);
}
