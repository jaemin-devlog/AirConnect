package univ.airconnect.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.chat.domain.entity.ChatRoomMember;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    /**
     * 특정 사용자가 특정 채팅방의 참여자인지 확인
     */
    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    /**
     * 특정 사용자가 참여 중인 채팅방 목록 조회
     */
    List<ChatRoomMember> findByUser_IdOrderByJoinedAtDesc(Long userId);

    /**
     * 특정 채팅방의 모든 참여자 조회
     */
    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);

    /**
     * 특정 채팅방의 특정 참여자 조회
     */
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
