package com.apigateway.admin.scheduler;

import com.apigateway.admin.repository.GrayReleaseRepository;
import com.apigateway.admin.service.GrayReleaseService;
import com.apigateway.common.entity.GrayRelease;
import com.apigateway.common.enums.GrayReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrayReleaseScheduler {

    private final GrayReleaseRepository grayReleaseRepository;
    private final GrayReleaseService grayReleaseService;

    @Scheduled(fixedRate = 60000)
    public void processGrayReleasePhases() {
        log.debug("Starting gray release phase processing");

        List<GrayReleaseStatus> activeStatuses = List.of(GrayReleaseStatus.IN_PROGRESS);
        List<GrayRelease> activeReleases = grayReleaseRepository.findByStatusIn(activeStatuses);

        for (GrayRelease release : activeReleases) {
            try {
                log.debug("Processing gray release phase for id: {}", release.getId());
                grayReleaseService.processGrayReleasePhase(release.getId());
            } catch (Exception e) {
                log.error("Failed to process gray release phase for id: {}", release.getId(), e);
            }
        }

        log.debug("Completed gray release phase processing, processed {} releases", activeReleases.size());
    }
}
