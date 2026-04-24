package com.ahy.knowledgepulse.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class InspirationResponse {

    private LocalDate date;

    private List<String> recommendedTags;

    private List<NoteResponse> relatedNotes;

    private String inspirationQuote;
}
