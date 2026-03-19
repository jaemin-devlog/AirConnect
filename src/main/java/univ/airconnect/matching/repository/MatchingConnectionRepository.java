package univ.airconnect.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;

import java.util.List;
import java.util.Optional;

public interface MatchingConnectionRepository extends JpaRepository<MatchingConnection, Long> {

    Optional<MatchingConnection> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    // 요청 보낸 목록
    List<MatchingConnection> findByRequesterId(Long requesterId);

    List<MatchingConnection> findByRequesterIdAndStatus(Long requesterId, ConnectionStatus status);

    // 요청 받은 목록
    @Query("""
        SELECT mc FROM MatchingConnection mc 
        WHERE ((mc.user1Id = :userId AND mc.user2Id != :userId) OR (mc.user2Id = :userId AND mc.user1Id != :userId))
        AND mc.requesterId != :userId
    """)
    List<MatchingConnection> findReceivedRequests(@Param("userId") Long userId);

    @Query("""
        SELECT mc FROM MatchingConnection mc 
        WHERE ((mc.user1Id = :userId AND mc.user2Id != :userId) OR (mc.user2Id = :userId AND mc.user1Id != :userId))
        AND mc.requesterId != :userId
        AND mc.status = :status
    """)
    List<MatchingConnection> findReceivedRequestsByStatus(@Param("userId") Long userId, @Param("status") ConnectionStatus status);
}



