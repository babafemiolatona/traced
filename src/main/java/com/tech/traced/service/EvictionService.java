package com.tech.traced.service;

import com.tech.traced.repository.TraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvictionService {

    private final TraceRepository traceRepository;

    @Value("${window.minutes:30}")
    private int windowMinutes;

    @Scheduled(fixedDelay = 10000)
    public void evictExpiredSpans() {
        try {
            long startTime = System.currentTimeMillis();

            Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);

            traceRepository.removeSpansOlderThan(cutoff);

            long duration = System.currentTimeMillis() - startTime;

            int traceCount = traceRepository.getTraceCount();
            int orphanCount = traceRepository.getOrphanCount();

            log.info("Eviction cycle completed in {}ms | traces={} | orphans={}",
                    duration, traceCount, orphanCount);

        } catch (Exception e) {
            log.error("Error during eviction cycle", e);
        }
    }
}
