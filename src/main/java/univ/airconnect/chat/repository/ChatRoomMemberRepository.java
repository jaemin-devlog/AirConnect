package univ.airconnect.chat.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.chat.domain.entity.ChatRoomMember;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    /**
     * 특정 사용자가 특정 채팅방의 참여자인지 확인
     */
    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);
    boolean existsByChatRoomIdAndUserIdAndHiddenAtIsNull(Long chatRoomId, Long userId);

    /**
     * 특정 사용자가 참여 중인 채팅방 목록 조회 (N+1 방지를 위해 ChatRoom Fetch Join)
     */
    @Query("SELECT m FROM ChatRoomMember m JOIN FETCH m.chatRoom WHERE m.user.id = :userId AND m.hiddenAt IS NULL ORDER BY m.joinedAt DESC")
    List<ChatRoomMember> findByUser_IdWithRoom(@Param("userId") Long userId);

    /**
     * 특정 채팅방의 모든 참여자 조회
     */
    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);

    @Query("SELECT m FROM ChatRoomMember m JOIN FETCH m.user u LEFT JOIN FETCH u.userProfile WHERE m.chatRoom.id IN :roomIds")
    List<ChatRoomMember> findByChatRoomIdInWithUser(@Param("roomIds") List<Long> roomIds);

    @Query("SELECT m.user.id FROM ChatRoomMember m WHERE m.chatRoom.id = :chatRoomId")
    List<Long> findUserIdsByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * 특정 채팅방의 특정 참여자 조회
     */
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    Optional<ChatRoomMember> findByChatRoomIdAndUserIdAndHiddenAtIsNull(Long chatRoomId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM ChatRoomMember m JOIN FETCH m.user " +
            "WHERE m.chatRoom.id = :chatRoomId AND m.user.id = :userId AND m.hiddenAt IS NULL")
    Optional<ChatRoomMember> findVisibleByChatRoomIdAndUserIdForUpdate(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") Long userId
    );

    @Query("SELECT m FROM ChatRoomMember m JOIN FETCH m.user u LEFT JOIN FETCH u.userProfile " +
            "WHERE m.chatRoom.id = :chatRoomId AND m.hiddenAt IS NULL " +
            "AND u.status = univ.airconnect.user.domain.UserStatus.ACTIVE ORDER BY m.joinedAt ASC")
    List<ChatRoomMember> findByChatRoomIdAndHiddenAtIsNullOrderByJoinedAtAsc(@Param("chatRoomId") Long chatRoomId);

    long countByChatRoomId(Long chatRoomId);

    /**
     * 두 사용자가 모두 참여 중인 PERSONAL 타입의 채팅방 ID 조회
     */
    @Query("SELECT m1.chatRoom.id FROM ChatRoomMember m1 " +
           "JOIN ChatRoomMember m2 ON m1.chatRoom.id = m2.chatRoom.id " +
           "WHERE m1.user.id = :user1Id AND m2.user.id = :user2Id " +
           "AND m1.chatRoom.type = 'PERSONAL'")
    List<Long> findCommonPersonalRoomIds(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);

    @Query(value = """
        SELECT COUNT(*)
        FROM (
            SELECT cr.id
            FROM chat_rooms cr
            LEFT JOIN chat_room_members crm
              ON crm.chat_room_id = cr.id
             AND crm.hidden_at IS NULL
            WHERE cr.type = 'PERSONAL'
            GROUP BY cr.id
            HAVING COUNT(crm.id) <> 2
        ) broken_rooms
    """, nativeQuery = true)
    long countPersonalRoomsWithInvalidVisibleMemberCount();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatRoomMember m WHERE m.user.id = :userId")
    long deleteByUserId(@Param("userId") Long userId);
}
