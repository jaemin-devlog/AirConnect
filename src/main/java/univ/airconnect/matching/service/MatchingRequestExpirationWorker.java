package univ.airconnect.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.matching.repository.MatchingConnectRequestRepository;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingRecommendationRequestRepository;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingRequestExpirationWorker {

    private final MatchingConnectionRepository matchingConnectionRepository;
    private final MatchingConnectRequestRepository matchingConnectRequestRepository;
    private final MatchingRecommendationRequestRepository matchingRecommendationRequestRepository;

    @Value("${matching.one-to-one.request-expiration-days:7}")
    private long expirationDays;

    @Value("${matching.one-to-one.idempotency-retention-days:30}")
    private long idempotencyRetentionDays;

    @Scheduled(fixedDelayString = "${matching.one-to-one.expiration-worker-delay-ms:300000}")
    @Transactional
    public void expireOverdueRequests() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        int expired = matchingConnectionRepository.expirePendingBefore(now.minusDays(expirationDays), now);
        if (expired > 0) {
            log.info("Expired overdue one-to-one matching requests: count={}", expired);
        }

        LocalDateTime idempotencyCutoff = now.minusDays(idempotencyRetentionDays);
        int deletedConnectRequests = matchingConnectRequestRepository.deleteByCreatedAtBefore(idempotencyCutoff);
        int deletedRecommendationRequests =
                matchingRecommendationRequestRepository.deleteByCreatedAtBefore(idempotencyCutoff);
        if (deletedConnectRequests > 0 || deletedRecommendationRequests > 0) {
            log.info(
                    "Deleted expired one-to-one idempotency records: connect={}, recommendation={}",
                    deletedConnectRequests,
                    deletedRecommendationRequests
            );
        }
    }
}
