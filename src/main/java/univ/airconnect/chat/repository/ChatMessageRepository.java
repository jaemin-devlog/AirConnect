package univ.airconnect.chat.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.chat.domain.entity.ChatMessage;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    @Query("SELECT m FROM ChatMessage m " +
            "WHERE m.roomId = :roomId " +
            "AND (:lastId IS NULL OR m.id < :lastId) " +
            "ORDER BY m.id DESC")
    List<ChatMessage> findMessagesCursor(@Param("roomId") Long roomId,
                                         @Param("lastId") Long lastId,
                                         Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.id IN (" +
            "SELECT MAX(m2.id) FROM ChatMessage m2 WHERE m2.roomId IN :roomIds GROUP BY m2.roomId)")
    List<ChatMessage> findLatestMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);

    @Query("SELECT m.roomId as roomId, COUNT(m.id) as unreadCount " +
            "FROM ChatMessage m, ChatRoomMember crm " +
            "WHERE m.roomId = crm.chatRoom.id " +
            "AND crm.user.id = :userId " +
            "AND m.senderId <> :userId " +
            "AND (crm.lastReadMessageId IS NULL OR m.id > crm.lastReadMessageId) " +
            "GROUP BY m.roomId")
    List<Object[]> countUnreadByUserId(@Param("userId") Long userId);

    Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);

    int countByRoomIdAndIdGreaterThan(Long roomId, Long lastReadMessageId);
}