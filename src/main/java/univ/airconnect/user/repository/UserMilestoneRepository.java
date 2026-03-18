package univ.airconnect.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.entity.UserMilestone;

import java.util.Optional;

public interface UserMilestoneRepository extends JpaRepository<UserMilestone, Long> {

    Optional<UserMilestone> findByUserIdAndMilestoneType(Long userId, MilestoneType milestoneType);

    boolean existsByUserIdAndMilestoneType(Long userId, MilestoneType milestoneType);
}

