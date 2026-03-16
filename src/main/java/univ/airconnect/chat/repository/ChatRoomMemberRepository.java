package univ.airconnect.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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

    /**
     * 특정 사용자가 참여 중인 채팅방 목록 조회 (N+1 방지를 위해 ChatRoom Fetch Join)
     */
    @Query("SELECT m FROM ChatRoomMember m JOIN FETCH m.chatRoom WHERE m.user.id = :userId ORDER BY m.joinedAt DESC")
    List<ChatRoomMember> findByUser_IdWithRoom(@Param("userId") Long userId);

    /**
     * 특정 채팅방의 모든 참여자 조회
     */
    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);

    /**
     * 특정 채팅방의 특정 참여자 조회
     */
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    /**
     * 두 사용자가 모두 참여 중인 PERSONAL 타입의 채팅방 ID 조회
     */
    @Query("SELECT m1.chatRoom.id FROM ChatRoomMember m1 " +
           "JOIN ChatRoomMember m2 ON m1.chatRoom.id = m2.chatRoom.id " +
           "WHERE m1.user.id = :user1Id AND m2.user.id = :user2Id " +
           "AND m1.chatRoom.type = 'PERSONAL'")
    List<Long> findCommonPersonalRoomIds(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
}
