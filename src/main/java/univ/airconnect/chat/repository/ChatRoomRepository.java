package univ.airconnect.chat.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	@Query("""
		SELECT COUNT(r)
		FROM ChatRoom r
		WHERE NOT EXISTS (
			SELECT 1
			FROM ChatRoomMember m
			WHERE m.chatRoom = r
			  AND m.hiddenAt IS NULL
		)
	""")
	long countRoomsWithoutVisibleMembers();

	@Query("""
		SELECT r
		FROM ChatRoom r
		WHERE (:type IS NULL OR r.type = :type)
		  AND (:userId IS NULL OR EXISTS (
		      SELECT 1
		      FROM ChatRoomMember m
		      WHERE m.chatRoom = r
		        AND m.user.id = :userId
		  ))
		  AND (:keyword IS NULL
		       OR LOWER(COALESCE(r.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
		       OR CAST(r.id AS string) = :keyword
		       OR CAST(r.connectionId AS string) = :keyword)
		ORDER BY r.updatedAt DESC, r.id DESC
	""")
	Page<ChatRoom> searchForAdmin(@Param("type") ChatRoomType type,
	                              @Param("userId") Long userId,
	                              @Param("keyword") String keyword,
	                              Pageable pageable);
}
