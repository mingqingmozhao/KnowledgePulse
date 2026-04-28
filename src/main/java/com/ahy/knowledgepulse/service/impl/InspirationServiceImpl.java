package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.InspirationMatchResponse;
import com.ahy.knowledgepulse.dto.response.InspirationResponse;
import com.ahy.knowledgepulse.dto.response.NoteResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.User;
import com.ahy.knowledgepulse.exception.BusinessException;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.mapper.NoteTagMapper;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspirationServiceImpl implements InspirationService {

    private static final String CACHE_KEY_PREFIX = "knowledgepulse:daily-inspiration:";
    private static final Duration CACHE_TTL = Duration.ofHours(36);
    private static final int RECENT_ACTIVITY_DAYS = 14;
    private static final int MAX_RECOMMENDED_TAGS = 6;
    private static final int MAX_RECOMMENDATIONS = 5;

    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public InspirationResponse getDailyInspiration() {
        Long userId = requireCurrentUser();
        LocalDate today = LocalDate.now();
        LocalDateTime latestActivityTime = noteMapper.findLatestAccessibleUpdateTime(userId);
        String cacheKey = buildCacheKey(userId, today, latestActivityTime);

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
        LocalDateTime latestActivityTime = noteMapper.findLatestAccessibleUpdateTime(userId);
        String cacheKey = buildCacheKey(userId, date, latestActivityTime);
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

    private String buildCacheKey(Long userId, LocalDate date, LocalDateTime latestActivityTime) {
        String activityFingerprint = latestActivityTime == null ? "empty" : latestActivityTime.toString();
        return CACHE_KEY_PREFIX + userId + ":" + date + ":" + activityFingerprint;
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

        LocalDateTime startTime = LocalDateTime.now().minusDays(RECENT_ACTIVITY_DAYS);
        List<Note> recentNotes = noteMapper.findAccessibleRecentNotes(userId, startTime);
        List<String> recommendedTags = resolveRecommendedTags(userId, recentNotes);
        List<ScoredNote> scoredNotes = findScoredMatches(userId, recommendedTags, recentNotes);

        response.setRecommendedTags(recommendedTags);
        response.setRelatedNotes(scoredNotes.stream().map(ScoredNote::note).map(this::convertToResponse).toList());
        response.setRecommendations(scoredNotes.stream().map(this::convertToRecommendation).toList());
        response.setInspirationPrompts(buildInspirationPrompts(recentNotes, recommendedTags, scoredNotes));
        response.setMatchSummary(buildMatchSummary(recentNotes, recommendedTags, scoredNotes));
        response.setInspirationQuote(buildDynamicInsight(recentNotes, recommendedTags, scoredNotes));
        return response;
    }

    private List<String> resolveRecommendedTags(Long userId, List<Note> recentNotes) {
        Map<String, Integer> weightedTags = new LinkedHashMap<>();

        for (int index = 0; index < recentNotes.size(); index++) {
            Note note = recentNotes.get(index);
            int noteWeight = Math.max(1, RECENT_ACTIVITY_DAYS - index + 1);

            for (String tag : resolveNoteTags(note)) {
                weightedTags.merge(tag, noteWeight, Integer::sum);
            }
        }

        List<String> tags = rankTags(weightedTags);
        if (!tags.isEmpty()) {
            return tags;
        }

        return noteTagMapper.findTopAccessibleTagsByUserId(userId).stream()
                .map(this::normalizeTag)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_RECOMMENDED_TAGS)
                .toList();
    }

    private List<String> rankTags(Map<String, Integer> weightedTags) {
        return weightedTags.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .map(Map.Entry::getKey)
                .limit(MAX_RECOMMENDED_TAGS)
                .toList();
    }

    private List<ScoredNote> findScoredMatches(Long userId, List<String> recommendedTags, List<Note> recentNotes) {
        if (recommendedTags.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> tagPriority = new LinkedHashMap<>();
        for (int index = 0; index < recommendedTags.size(); index++) {
            tagPriority.put(recommendedTags.get(index).toLowerCase(), MAX_RECOMMENDED_TAGS - index);
        }

        Set<Long> recentNoteIds = new LinkedHashSet<>();
        for (Note note : recentNotes) {
            if (note.getId() != null) {
                recentNoteIds.add(note.getId());
            }
        }

        return noteMapper.findAccessibleNotesByTags(userId, recommendedTags).stream()
                .map(note -> scoreNote(note, tagPriority, recentNoteIds))
                .filter(scoredNote -> !scoredNote.matchedTags().isEmpty())
                .sorted(
                        Comparator.comparingInt(ScoredNote::score)
                                .reversed()
                                .thenComparing(scoredNote -> scoredNote.note().getUpdateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                )
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    private ScoredNote scoreNote(Note note, Map<String, Integer> tagPriority, Set<Long> recentNoteIds) {
        List<String> matchedTags = resolveNoteTags(note).stream()
                .filter(tag -> tagPriority.containsKey(tag.toLowerCase()))
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                ArrayList::new
                        )
                );

        int tagScore = matchedTags.stream()
                .map(tag -> tagPriority.getOrDefault(tag.toLowerCase(), 0))
                .reduce(0, Integer::sum);
        int score = matchedTags.size() * 100 + tagScore * 10 + recencyScore(note.getUpdateTime());

        if (!recentNoteIds.contains(note.getId())) {
            score += 25;
        }

        return new ScoredNote(note, matchedTags, score);
    }

    private int recencyScore(LocalDateTime updateTime) {
        if (updateTime == null) {
            return 0;
        }

        long days = Math.max(0, Duration.between(updateTime, LocalDateTime.now()).toDays());
        return (int) Math.max(0, RECENT_ACTIVITY_DAYS - Math.min(days, RECENT_ACTIVITY_DAYS));
    }

    private List<String> resolveNoteTags(Note note) {
        List<String> tagsFromTable = note.getId() == null ? List.of() : noteTagMapper.findTagsByNoteId(note.getId());
        List<String> sourceTags = tagsFromTable == null || tagsFromTable.isEmpty()
                ? splitLegacyTags(note.getTags())
                : tagsFromTable;

        return sourceTags.stream()
                .map(this::normalizeTag)
                .filter(StringUtils::hasText)
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                ArrayList::new
                        )
                );
    }

    private List<String> splitLegacyTags(String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return List.of();
        }

        return List.of(rawTags.split(","));
    }

    private String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim();
    }

    private String buildMatchSummary(List<Note> recentNotes, List<String> recommendedTags, List<ScoredNote> scoredNotes) {
        if (recommendedTags.isEmpty()) {
            return "还没有足够的标签数据。给笔记添加标签后，系统会自动从真实标签关系里匹配推荐。";
        }

        String source = recentNotes.isEmpty()
                ? "基于当前知识库的高频标签"
                : "基于最近 " + RECENT_ACTIVITY_DAYS + " 天更新的 " + recentNotes.size() + " 篇笔记";
        return source + "，匹配出 " + scoredNotes.size() + " 篇可继续串联的同标签笔记。";
    }

    private String buildDynamicInsight(List<Note> recentNotes, List<String> recommendedTags, List<ScoredNote> scoredNotes) {
        if (recommendedTags.isEmpty()) {
            return "先给最近的笔记补上标签，灵感推荐会自动变成真正贴合你知识库的主题线索。";
        }

        String tagText = formatTags(recommendedTags.stream().limit(3).toList());
        if (scoredNotes.isEmpty()) {
            return "最近的主题集中在 " + tagText + "，继续写几篇同标签笔记后，这里会自动串联出可延展的相关内容。";
        }

        String activityText = recentNotes.isEmpty() ? "你的知识库" : "最近的编辑痕迹";
        return activityText + "正在靠近 " + tagText + "，系统已按真实标签匹配到 "
                + scoredNotes.size() + " 篇相关笔记，可以从最高匹配项继续展开。";
    }

    private List<String> buildInspirationPrompts(List<Note> recentNotes, List<String> recommendedTags, List<ScoredNote> scoredNotes) {
        if (recommendedTags.isEmpty()) {
            return List.of("给最近一篇笔记补 2-3 个标签，系统就能从真实标签关系里继续生成灵感。");
        }

        List<String> prompts = new ArrayList<>();
        String topTag = recommendedTags.get(0);
        String tagText = formatTags(recommendedTags.stream().limit(3).toList());

        if (!scoredNotes.isEmpty()) {
            ScoredNote firstMatch = scoredNotes.get(0);
            prompts.add("围绕 " + formatTags(firstMatch.matchedTags()) + "，把《" + firstMatch.note().getTitle() + "》补成一段“下一步可以怎么用”。");
        }

        if (!recentNotes.isEmpty()) {
            Note latestNote = recentNotes.get(0);
            prompts.add("从《" + latestNote.getTitle() + "》出发，写下它和 " + tagText + " 的共同问题或待验证假设。");
        }

        prompts.add("新建一条 #" + topTag + " 主题的追问：它解决了什么、还缺什么、可以连接到哪篇旧笔记？");

        return prompts.stream()
                .filter(StringUtils::hasText)
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                list -> list.stream().limit(3).toList()
                        )
                );
    }

    private InspirationMatchResponse convertToRecommendation(ScoredNote scoredNote) {
        InspirationMatchResponse response = new InspirationMatchResponse();
        response.setNoteId(scoredNote.note().getId());
        response.setTitle(scoredNote.note().getTitle());
        response.setMatchedTags(scoredNote.matchedTags());
        response.setScore(scoredNote.score());
        response.setReason("匹配 " + formatTags(scoredNote.matchedTags()) + "，适合和近期主题继续串联。");
        response.setUpdateTime(scoredNote.note().getUpdateTime());
        return response;
    }

    private String formatTags(List<String> tags) {
        if (tags.isEmpty()) {
            return "相关标签";
        }

        return tags.stream()
                .map(tag -> "#" + tag)
                .reduce((left, right) -> left + "、" + right)
                .orElse("相关标签");
    }

    private NoteResponse convertToResponse(Note note) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setTitle(note.getTitle());
        response.setContent(note.getContent());
        response.setHtmlContent(note.getHtmlContent());
        response.setTags(resolveNoteTags(note));
        response.setFolderId(note.getFolderId());
        response.setCreateTime(note.getCreateTime());
        response.setUpdateTime(note.getUpdateTime());
        return response;
    }

    private record ScoredNote(Note note, List<String> matchedTags, int score) {
    }
}
