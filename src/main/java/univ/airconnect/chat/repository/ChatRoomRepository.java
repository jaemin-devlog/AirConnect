package univ.airconnect.chat.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	Optional<ChatRoom> findByConnectionId(Long connectionId);

	Optional<ChatRoom> findByTypeAndUser1IdAndUser2Id(ChatRoomType type, Long user1Id, Long user2Id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM ChatRoom r WHERE r.id = :roomId")
	Optional<ChatRoom> findByIdForUpdate(@Param("roomId") Long roomId);
}
