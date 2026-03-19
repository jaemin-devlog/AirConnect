package univ.airconnect.user.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.user.domain.MilestoneType;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_milestones",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "milestone_type"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MilestoneType milestoneType;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    @Column(nullable = false)
    private Boolean granted = true;

    private UserMilestone(Long userId, MilestoneType milestoneType, LocalDateTime grantedAt) {
        this.userId = userId;
        this.milestoneType = milestoneType;
        this.grantedAt = grantedAt;
        this.granted = true;
    }

    public static UserMilestone create(Long userId, MilestoneType milestoneType) {
        return new UserMilestone(userId, milestoneType, LocalDateTime.now());
    }
}

