package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.response.SearchResult;

import java.util.List;

public interface SearchService {

    List<SearchResult> search(String keyword);

    List<SearchResult> searchByTag(String tagName);
}
