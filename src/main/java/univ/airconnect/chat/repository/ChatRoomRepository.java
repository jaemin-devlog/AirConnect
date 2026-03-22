package univ.airconnect.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	Optional<ChatRoom> findByConnectionId(Long connectionId);

	Optional<ChatRoom> findByTypeAndUser1IdAndUser2Id(ChatRoomType type, Long user1Id, Long user2Id);
}
