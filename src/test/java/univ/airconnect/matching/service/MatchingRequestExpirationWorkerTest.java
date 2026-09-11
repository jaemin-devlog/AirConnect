package univ.airconnect.matching.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnectRequest;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.domain.entity.MatchingRecommendationRequest;
import univ.airconnect.matching.repository.MatchingConnectRequestRepository;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingRecommendationRequestRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(MatchingRequestExpirationWorker.class)
class MatchingRequestExpirationWorkerTest {

    @Autowired
    private MatchingRequestExpirationWorker worker;

    @Autowired
    private MatchingConnectionRepository matchingConnectionRepository;

    @Autowired
    private MatchingConnectRequestRepository matchingConnectRequestRepository;

    @Autowired
    private MatchingRecommendationRequestRepository matchingRecommendationRequestRepository;

    @Test
    void expiresOnlyPendingRequestsOlderThanSevenDays() {
        MatchingConnection overdue = MatchingConnection.createPending(1L, 2L);
        MatchingConnection recent = MatchingConnection.createPending(3L, 4L);
        ReflectionTestUtils.setField(
                overdue,
                "connectedAt",
                LocalDateTime.now(Clock.systemUTC()).minusDays(8)
        );
        matchingConnectionRepository.save(overdue);
        matchingConnectionRepository.save(recent);
        matchingConnectionRepository.flush();
        ReflectionTestUtils.setField(worker, "expirationDays", 7L);

        worker.expireOverdueRequests();

        assertThat(matchingConnectionRepository.findById(overdue.getId()).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.EXPIRED);
        assertThat(matchingConnectionRepository.findById(recent.getId()).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.PENDING);
    }

    @Test
    void deletesOnlyIdempotencyRecordsOlderThanRetentionPeriod() {
        MatchingConnectRequest oldConnect = MatchingConnectRequest.create(
                1L, "old-connect", 2L, 10L, null, false
        );
        MatchingConnectRequest recentConnect = MatchingConnectRequest.create(
                1L, "recent-connect", 3L, 11L, null, false
        );
        MatchingRecommendationRequest oldRecommendation = MatchingRecommendationRequest.create(
                1L, "old-recommendation", false, List.of(2L), 9
        );
        MatchingRecommendationRequest recentRecommendation = MatchingRecommendationRequest.create(
                1L, "recent-recommendation", false, List.of(3L), 8
        );
        LocalDateTime oldCreatedAt = LocalDateTime.now(Clock.systemUTC()).minusDays(31);
        ReflectionTestUtils.setField(oldConnect, "createdAt", oldCreatedAt);
        ReflectionTestUtils.setField(oldRecommendation, "createdAt", oldCreatedAt);

        matchingConnectRequestRepository.saveAll(List.of(oldConnect, recentConnect));
        matchingRecommendationRequestRepository.saveAll(List.of(oldRecommendation, recentRecommendation));
        matchingConnectRequestRepository.flush();
        matchingRecommendationRequestRepository.flush();
        ReflectionTestUtils.setField(worker, "idempotencyRetentionDays", 30L);

        worker.expireOverdueRequests();

        assertThat(matchingConnectRequestRepository.findByUserIdAndRequestKey(1L, "old-connect")).isEmpty();
        assertThat(matchingConnectRequestRepository.findByUserIdAndRequestKey(1L, "recent-connect")).isPresent();
        assertThat(matchingRecommendationRequestRepository
                .findByUserIdAndRequestKey(1L, "old-recommendation")).isEmpty();
        assertThat(matchingRecommendationRequestRepository
                .findByUserIdAndRequestKey(1L, "recent-recommendation")).isPresent();
    }
}
