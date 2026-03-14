package univ.airconnect.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.chat.domain.entity.ChatMessage;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    Optional<ChatMessage> findTopByRoomIdOrderByCreatedAtDesc(Long roomId);
}
