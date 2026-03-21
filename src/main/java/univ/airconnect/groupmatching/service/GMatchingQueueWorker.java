package univ.airconnect.groupmatching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.groupmatching.domain.GTeamSize;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "matching.queue.worker.enabled", havingValue = "true", matchIfMissing = false)
public class GMatchingQueueWorker {

    private static final int DEFAULT_SCAN_SIZE = 100;
    private static final int DEFAULT_MAX_MATCHES_PER_TICK = 20;

    private final GMatchingService matchingService;

    @Scheduled(fixedDelayString = "${matching.queue.worker.delay-ms:1000}")
    public void drainQueues() {
        drainQueue(GTeamSize.TWO);
        drainQueue(GTeamSize.THREE);
    }

    private void drainQueue(GTeamSize teamSize) {
        for (int i = 0; i < DEFAULT_MAX_MATCHES_PER_TICK; i++) {
            GMatchingService.MatchSuccessResult matchResult = matchingService.processQueue(teamSize, DEFAULT_SCAN_SIZE);
            if (matchResult == null) {
                break;
            }
            log.info("Queue worker matched: teamSize={}, finalGroupRoomId={}",
                    teamSize, matchResult.finalGroupRoomId());
        }
    }
}
