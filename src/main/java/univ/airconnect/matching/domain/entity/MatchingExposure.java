package univ.airconnect.matching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "matching_exposures",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "candidate_user_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingExposure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "candidate_user_id", nullable = false)
    private Long candidateUserId;

    @Column(name = "exposed_at", nullable = false)
    private LocalDateTime exposedAt;

    private MatchingExposure(Long userId, Long candidateUserId, LocalDateTime exposedAt) {
        this.userId = userId;
        this.candidateUserId = candidateUserId;
        this.exposedAt = exposedAt;
    }

    public static MatchingExposure create(Long userId, Long candidateUserId) {
        return new MatchingExposure(userId, candidateUserId, LocalDateTime.now());
    }
}


