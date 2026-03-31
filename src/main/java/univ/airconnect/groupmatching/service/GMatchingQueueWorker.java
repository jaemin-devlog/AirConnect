package univ.airconnect.groupmatching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import univ.airconnect.groupmatching.domain.GTeamSize;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "matching.queue.worker.enabled", havingValue = "true", matchIfMissing = false)
public class GMatchingQueueWorker {

    private final GMatchingService matchingService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAndDrainOnStartup() {
        drainQueues();
    }

    @Scheduled(fixedDelayString = "${matching.queue.worker.delay-ms:1000}")
    public void drainQueues() {
        drainQueue(GTeamSize.TWO);
        drainQueue(GTeamSize.THREE);
    }

    private void drainQueue(GTeamSize teamSize) {
        GMatchingService.QueueReconcileResult reconcileResult = matchingService.reconcileQueue(teamSize);
        if (reconcileResult.lockAcquired() && (reconcileResult.rebuilt() || reconcileResult.metadataRecovered())) {
            log.info(
                    "Queue worker reconciled: teamSize={}, rebuilt={}, metadataRecovered={}, waitingTeamCount={}, redisQueueCount={}",
                    teamSize,
                    reconcileResult.rebuilt(),
                    reconcileResult.metadataRecovered(),
                    reconcileResult.waitingTeamCount(),
                    reconcileResult.redisQueueCount()
            );
        }

        int matchedCount = matchingService.processQueueUntilStable(teamSize);
        if (matchedCount > 0) {
            log.info("Queue worker matched: teamSize={}, matchedCount={}", teamSize, matchedCount);
        }
    }
}
