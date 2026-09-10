package univ.airconnect.matching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "matching_recommendation_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_matching_recommendation_user_key",
                columnNames = {"user_id", "request_key"}
        ),
        indexes = @Index(name = "idx_matching_recommendation_created", columnList = "created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRecommendationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;

    @Column(name = "same_gender_only", nullable = false)
    private boolean sameGenderOnly;

    @Column(name = "candidate_user_ids", nullable = false, length = 100)
    private String candidateUserIds;

    @Column(name = "tickets_remaining", nullable = false)
    private Integer ticketsRemaining;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static MatchingRecommendationRequest create(Long userId,
                                                       String requestKey,
                                                       boolean sameGenderOnly,
                                                       List<Long> candidateUserIds,
                                                       int ticketsRemaining) {
        MatchingRecommendationRequest request = new MatchingRecommendationRequest();
        request.userId = userId;
        request.requestKey = requestKey;
        request.sameGenderOnly = sameGenderOnly;
        request.candidateUserIds = candidateUserIds == null || candidateUserIds.isEmpty()
                ? ""
                : candidateUserIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        request.ticketsRemaining = ticketsRemaining;
        request.createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
        return request;
    }

    public List<Long> candidateIds() {
        if (candidateUserIds == null || candidateUserIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(candidateUserIds.split(","))
                .map(Long::valueOf)
                .toList();
    }
}
