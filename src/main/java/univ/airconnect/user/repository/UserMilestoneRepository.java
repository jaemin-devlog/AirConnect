package univ.airconnect.user.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.entity.UserMilestone;

import java.util.Optional;

public interface UserMilestoneRepository extends JpaRepository<UserMilestone, Long> {

    Optional<UserMilestone> findByUserIdAndMilestoneType(Long userId, MilestoneType milestoneType);

    // Locking read also sees the latest committed grant under repeatable-read isolation.
    // Ticket writers acquire the user lock before checking this row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UserMilestone m where m.userId = :userId and m.milestoneType = :milestoneType")
    Optional<UserMilestone> findByUserIdAndMilestoneTypeForUpdate(
            @Param("userId") Long userId, @Param("milestoneType") MilestoneType milestoneType);

    boolean existsByUserIdAndMilestoneType(Long userId, MilestoneType milestoneType);

    boolean existsByUserIdAndMilestoneTypeAndGrantedTrue(Long userId, MilestoneType milestoneType);

    long deleteByUserId(Long userId);
}
