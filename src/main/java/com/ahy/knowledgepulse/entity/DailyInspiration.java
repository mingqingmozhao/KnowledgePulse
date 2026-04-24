package com.ahy.knowledgepulse.entity;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DailyInspiration {

    private LocalDate date;
    
    private List<String> recommendedTags;
    
    private List<Note> relatedNotes;
    
    private String inspirationQuote;
}
