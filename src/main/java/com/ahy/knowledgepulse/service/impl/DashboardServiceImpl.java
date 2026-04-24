package com.ahy.knowledgepulse.service.impl;

import com.ahy.knowledgepulse.dto.response.DashboardResponse;
import com.ahy.knowledgepulse.entity.Note;
import com.ahy.knowledgepulse.entity.NoteFolder;
import com.ahy.knowledgepulse.mapper.NoteFolderMapper;
import com.ahy.knowledgepulse.mapper.NoteMapper;
import com.ahy.knowledgepulse.service.DashboardService;
import com.ahy.knowledgepulse.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final NoteMapper noteMapper;
    private final NoteFolderMapper folderMapper;

    @Override
    public DashboardResponse getDashboardData() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<Note> notes = noteMapper.findByUserId(userId);
        List<NoteFolder> folders = folderMapper.findByUserId(userId);

        DashboardResponse response = new DashboardResponse();
        response.setTotalNotes((long) notes.size());
        response.setTotalFolders((long) folders.size());
        response.setTagDistribution(buildTagDistribution(notes));
        response.setTotalTags((long) response.getTagDistribution().size());
        response.setEditHeatmap(buildEditHeatmap(notes));
        return response;
    }

    private Map<String, Long> buildTagDistribution(List<Note> notes) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Note note : notes) {
            if (note.getTags() == null || note.getTags().isBlank()) {
                continue;
            }

            for (String rawTag : note.getTags().split(",")) {
                String tag = rawTag.trim();
                if (!tag.isEmpty()) {
                    distribution.merge(tag, 1L, Long::sum);
                }
            }
        }
        return distribution;
    }

    private Map<String, Integer> buildEditHeatmap(List<Note> notes) {
        Map<String, Integer> heatmap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            heatmap.put(date.toString(), 0);
        }

        for (Note note : notes) {
            if (note.getUpdateTime() == null) {
                continue;
            }

            String dateKey = note.getUpdateTime().toLocalDate().toString();
            if (heatmap.containsKey(dateKey)) {
                heatmap.merge(dateKey, 1, Integer::sum);
            }
        }

        return heatmap;
    }
}
