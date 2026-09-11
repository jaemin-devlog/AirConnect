package univ.airconnect.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.matching.repository.MatchingConnectRequestRepository;
import univ.airconnect.matching.repository.MatchingNotificationEventRepository;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.matching.repository.MatchingRecommendationRequestRepository;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MatchingLifecycleService {

    private final MatchingConnectionRepository matchingConnectionRepository;
    private final MatchingExposureRepository matchingExposureRepository;
    private final MatchingRecommendationRequestRepository matchingRecommendationRequestRepository;
    private final MatchingConnectRequestRepository matchingConnectRequestRepository;
    private final MatchingNotificationEventRepository matchingNotificationEventRepository;

    @Transactional
    public int cancelPendingBetweenUsers(Long userAId, Long userBId) {
        return matchingConnectionRepository.cancelPendingBetweenUsers(
                userAId,
                userBId,
                LocalDateTime.now(Clock.systemUTC())
        );
    }

    @Transactional
    public void cleanupOnAccountDeletion(Long userId) {
        matchingConnectionRepository.cancelPendingForUser(userId, LocalDateTime.now(Clock.systemUTC()));
        deleteUserMatchingArtifacts(userId);
    }

    @Transactional
    public void deleteUserMatchingArtifacts(Long userId) {
        matchingNotificationEventRepository.deleteByUserIdOrActorUserId(userId, userId);
        matchingConnectRequestRepository.deleteByUserIdOrTargetUserId(userId, userId);
        matchingRecommendationRequestRepository.deleteByOwnerOrCandidate(userId, String.valueOf(userId));
        matchingExposureRepository.deleteByUserIdOrCandidateUserId(userId, userId);
    }
}
