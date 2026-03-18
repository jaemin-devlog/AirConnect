package univ.airconnect.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.matching.domain.entity.MatchingConnection;

import java.util.Optional;

public interface MatchingConnectionRepository extends JpaRepository<MatchingConnection, Long> {

    Optional<MatchingConnection> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);
}

