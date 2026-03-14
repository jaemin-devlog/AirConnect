package univ.airconnect.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.chat.domain.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
}
