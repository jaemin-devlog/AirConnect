package univ.airconnect.matching.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;

import java.util.Collection;
import java.util.List;

public interface MatchingConnectionRepository extends JpaRepository<MatchingConnection, Long>, MatchingConnectionLockRepository {

    List<MatchingConnection> findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(Long user1Id, Long user2Id);

    long deleteByUser1IdOrUser2Id(Long user1Id, Long user2Id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE MatchingConnection mc
        SET mc.status = univ.airconnect.matching.domain.ConnectionStatus.CANCELLED,
            mc.respondedAt = :endedAt
        WHERE mc.status = univ.airconnect.matching.domain.ConnectionStatus.PENDING
          AND ((mc.user1Id = :userAId AND mc.user2Id = :userBId)
            OR (mc.user1Id = :userBId AND mc.user2Id = :userAId))
    """)
    int cancelPendingBetweenUsers(@Param("userAId") Long userAId,
                                  @Param("userBId") Long userBId,
                                  @Param("endedAt") java.time.LocalDateTime endedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE MatchingConnection mc
        SET mc.status = univ.airconnect.matching.domain.ConnectionStatus.CANCELLED,
            mc.respondedAt = :endedAt
        WHERE mc.status = univ.airconnect.matching.domain.ConnectionStatus.PENDING
          AND (mc.user1Id = :userId OR mc.user2Id = :userId)
    """)
    int cancelPendingForUser(@Param("userId") Long userId,
                             @Param("endedAt") java.time.LocalDateTime endedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE MatchingConnection mc
        SET mc.status = univ.airconnect.matching.domain.ConnectionStatus.EXPIRED,
            mc.respondedAt = :expiredAt
        WHERE mc.status = univ.airconnect.matching.domain.ConnectionStatus.PENDING
          AND mc.connectedAt < :cutoff
    """)
    int expirePendingBefore(@Param("cutoff") java.time.LocalDateTime cutoff,
                            @Param("expiredAt") java.time.LocalDateTime expiredAt);

    // 요청 보낸 목록
    List<MatchingConnection> findByRequesterId(Long requesterId);

    List<MatchingConnection> findByRequesterIdAndStatus(Long requesterId, ConnectionStatus status);

    @Query("""
        SELECT mc
        FROM MatchingConnection mc
        WHERE mc.requesterId = :requesterId
        ORDER BY COALESCE(mc.respondedAt, mc.connectedAt) DESC, mc.id DESC
    """)
    List<MatchingConnection> findTop20ByRequesterIdOrderByRecentDesc(@Param("requesterId") Long requesterId);

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

    long countByStatus(ConnectionStatus status);

    long countByStatusAndRespondedAtGreaterThanEqual(ConnectionStatus status, java.time.LocalDateTime since);

    long countByStatusInAndRespondedAtGreaterThanEqual(Collection<ConnectionStatus> statuses,
                                                       java.time.LocalDateTime since);

    long countByStatusAndChatRoomIdIsNotNullAndRespondedAtGreaterThanEqual(ConnectionStatus status, java.time.LocalDateTime since);

    long countByStatusAndChatRoomIdIsNull(ConnectionStatus status);

    long countByConnectedAtGreaterThanEqual(java.time.LocalDateTime since);

    @Query(value = """
        SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, connected_at, responded_at)), 0)
        FROM matching_connections
        WHERE responded_at IS NOT NULL
          AND connected_at >= :since
    """, nativeQuery = true)
    Double averageResponseSecondsSince(@Param("since") java.time.LocalDateTime since);

    @Query("""
        SELECT mc
        FROM MatchingConnection mc
        WHERE (:status IS NULL OR mc.status = :status)
          AND (:userId IS NULL OR mc.user1Id = :userId OR mc.user2Id = :userId)
        ORDER BY COALESCE(mc.respondedAt, mc.connectedAt) DESC, mc.id DESC
    """)
    Page<MatchingConnection> searchForAdmin(@Param("status") ConnectionStatus status,
                                            @Param("userId") Long userId,
                                            Pageable pageable);

    @Query(value = """
        SELECT COUNT(*)
        FROM matching_connections mc
        WHERE mc.status = 'ACCEPTED'
          AND mc.chat_room_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM chat_rooms cr
              WHERE cr.id = mc.chat_room_id
          )
    """, nativeQuery = true)
    long countAcceptedConnectionsWithMissingChatRoom();

}
