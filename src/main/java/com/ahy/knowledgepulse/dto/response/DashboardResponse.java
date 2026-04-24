package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class DashboardResponse {

    private Long totalNotes;

    private Long totalFolders;

    private Long totalTags;

    private Map<String, Long> tagDistribution;

    private Map<String, Integer> editHeatmap;
}
