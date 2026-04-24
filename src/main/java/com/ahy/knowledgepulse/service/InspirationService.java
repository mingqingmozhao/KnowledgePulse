package com.ahy.knowledgepulse.service;

import com.ahy.knowledgepulse.dto.response.InspirationResponse;

public interface InspirationService {

    InspirationResponse getDailyInspiration();

    void generateDailyInspiration();

    void warmupDailyInspirations();
}
