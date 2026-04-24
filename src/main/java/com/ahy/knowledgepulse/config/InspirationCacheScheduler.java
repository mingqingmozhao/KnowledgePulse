package com.ahy.knowledgepulse.config;

import com.ahy.knowledgepulse.service.InspirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InspirationCacheScheduler {

    private final InspirationService inspirationService;

    @Scheduled(cron = "0 0 8 * * ?")
    public void warmupTodayInspirationCache() {
        inspirationService.warmupDailyInspirations();
        log.info("Completed daily inspiration cache warmup");
    }
}
