package com.naturaldrops.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegularOrderPromptService {

    private final RegularOrderService regularOrderService;

    @Scheduled(fixedDelay = 60000, initialDelay = 20000)
    public void promptMorningRegularOrders() {
        try {
            regularOrderService.promptDueSellers();
        } catch (Exception ex) {
            log.error("Could not send regular order prompt: {}", ex.getMessage());
        }
    }
}
