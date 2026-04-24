package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.InspirationResponse;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.UserMapper;
import com.ahy.knowledgepulse.service.InspirationService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspirationServiceImpl implements InspirationService {

    private static final List<String> QUOTES = List.of(
            "Knowledge grows when it is revisited and connected.",
            "Write ideas down before they drift away.",
            "A small note captured today saves a long search tomorrow.",
            "Connections between notes are where insight begins.",
            "Your second brain becomes useful through steady curation."
    );

    private static final String CACHE_KEY_PREFIX = "knowledgepulse:daily-inspiration:";
    private static final Duration CACHE_TTL = Duration.ofHours(36);

    private final NoteMapper noteMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public InspirationResponse getDailyInspiration() {
        Long userId = requireCurrentUser();
        LocalDate today = LocalDate.now();
        String cacheKey = buildCacheKey(userId, today);

        InspirationResponse cached = readFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        InspirationResponse response = buildInspiration(userId, today);
        writeToCache(cacheKey, response);
        return response;
    }

    @Override
    public void generateDailyInspiration() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            log.debug("Skip daily inspiration generation because no authenticated user is present");
            return;
        }

        warmupSingleUser(userId, LocalDate.now(), true);
    }

    @Override
    public void warmupDailyInspirations() {
        LocalDate today = LocalDate.now();
        List<User> users = userMapper.selectList(null);
        int warmedCount = 0;

        for (User user : users) {
            if (user.getId() == null) {
                continue;
            }

            if (warmupSingleUser(user.getId(), today, false)) {
                warmedCount++;
            }
        }

        log.info("Daily inspiration cache warmup finished for {} users", warmedCount);
    }

    private boolean warmupSingleUser(Long userId, LocalDate date, boolean forceRefresh) {
        String cacheKey = buildCacheKey(userId, date);
        if (!forceRefresh && readFromCache(cacheKey) != null) {
            return false;
        }

        InspirationResponse response = buildInspiration(userId, date);
        writeToCache(cacheKey, response);
        return true;
    }

    private Long requireCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "User is not authenticated");
        }
        return userId;
    }

    private String buildCacheKey(Long userId, LocalDate date) {
        return CACHE_KEY_PREFIX + userId + ":" + date;
    }

    private InspirationResponse readFromCache(String cacheKey) {
        try {
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (!(cachedValue instanceof String cachedJson) || !StringUtils.hasText(cachedJson)) {
                return null;
            }

            return objectMapper.readValue(cachedJson, InspirationResponse.class);
        } catch (Exception ex) {
            log.warn("Failed to read inspiration cache key {}: {}", cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeToCache(String cacheKey, InspirationResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Failed to write inspiration cache key {}: {}", cacheKey, ex.getMessage());
        }
    }

    private InspirationResponse buildInspiration(Long userId, LocalDate date) {
        InspirationResponse response = new InspirationResponse();
        response.setDate(date);

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Note> recentNotes = noteMapper.findRecentNotes(userId, sevenDaysAgo);
        List<String> tags = extractTagsFromNotes(recentNotes);

        response.setRecommendedTags(tags.stream().limit(5).toList());
        response.setRelatedNotes(findRelatedNotes(userId, tags).stream().limit(5).map(this::convertToResponse).toList());
        response.setInspirationQuote(resolveQuote(userId, date));
        return response;
    }

    private String resolveQuote(Long userId, LocalDate date) {
        int quoteIndex = Math.floorMod(Objects.hash(userId, date), QUOTES.size());
        return QUOTES.get(quoteIndex);
    }

    private List<String> extractTagsFromNotes(List<Note> notes) {
        Set<String> tags = new LinkedHashSet<>();
        for (Note note : notes) {
            if (note.getTags() == null || note.getTags().isBlank()) {
                continue;
            }

            for (String rawTag : note.getTags().split(",")) {
                String tag = rawTag.trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private List<Note> findRelatedNotes(Long userId, List<String> tags) {
        Set<Note> relatedNotes = new LinkedHashSet<>();
        for (String tag : tags) {
            relatedNotes.addAll(noteMapper.findNotesWithTag(userId, tag));
        }
        return new ArrayList<>(relatedNotes);
    }

    private NoteResponse convertToResponse(Note note) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setTitle(note.getTitle());
        response.setContent(note.getContent());
        response.setFolderId(note.getFolderId());
        response.setCreateTime(note.getCreateTime());
        response.setUpdateTime(note.getUpdateTime());
        return response;
    }
}
