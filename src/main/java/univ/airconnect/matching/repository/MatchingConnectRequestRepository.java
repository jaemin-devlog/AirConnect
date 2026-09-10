package univ.airconnect.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.matching.domain.entity.MatchingConnectRequest;

import java.util.Optional;

public interface MatchingConnectRequestRepository extends JpaRepository<MatchingConnectRequest, Long> {

    Optional<MatchingConnectRequest> findByUserIdAndRequestKey(Long userId, String requestKey);
}
