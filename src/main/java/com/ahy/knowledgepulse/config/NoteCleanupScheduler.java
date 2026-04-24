package com.ahy.knowledgepulse.config;

import com.ahy.knowledgepulse.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteCleanupScheduler {

    private final NoteService noteService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredTrash() {
        noteService.cleanupExpiredDeletedNotes();
        log.info("Completed recycle-bin cleanup task");
    }
}
