package univ.airconnect.matching.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import univ.airconnect.matching.domain.entity.MatchingConnectRequest;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.domain.entity.MatchingExposure;
import univ.airconnect.matching.domain.entity.MatchingRecommendationRequest;
import univ.airconnect.matching.repository.MatchingConnectRequestRepository;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.matching.repository.MatchingRecommendationRequestRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(MatchingLifecycleService.class)
class MatchingLifecycleServiceTest {

    @Autowired private MatchingLifecycleService lifecycleService;
    @Autowired private MatchingConnectionRepository connectionRepository;
    @Autowired private MatchingExposureRepository exposureRepository;
    @Autowired private MatchingRecommendationRequestRepository recommendationRequestRepository;
    @Autowired private MatchingConnectRequestRepository connectRequestRepository;

    @Test
    void accountDeletionCancelsPendingAndRemovesIdempotencyArtifacts() {
        MatchingConnection connection = connectionRepository.save(MatchingConnection.createPending(1L, 2L));
        exposureRepository.save(MatchingExposure.create(1L, 2L));
        recommendationRequestRepository.save(MatchingRecommendationRequest.create(
                1L, "recommend-key", false, List.of(2L), 9
        ));
        connectRequestRepository.save(MatchingConnectRequest.create(
                1L, "connect-key", 2L, connection.getId(), null, false
        ));

        lifecycleService.cleanupOnAccountDeletion(2L);

        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getStatus())
                .isEqualTo(univ.airconnect.matching.domain.ConnectionStatus.CANCELLED);
        assertThat(exposureRepository.count()).isZero();
        assertThat(recommendationRequestRepository.count()).isZero();
        assertThat(connectRequestRepository.count()).isZero();
    }
}
