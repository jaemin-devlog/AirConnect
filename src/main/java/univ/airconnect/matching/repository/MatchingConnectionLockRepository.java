package univ.airconnect.matching.repository;

import univ.airconnect.matching.domain.entity.MatchingConnection;

import java.util.Optional;

public interface MatchingConnectionLockRepository {
    Optional<MatchingConnection> findByIdForUpdate(Long connectionId);
}
