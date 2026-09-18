package univ.airconnect.statistics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "department_ranking_baseline")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepartmentRankingBaseline {

    @Id
    private Long id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    private DepartmentRankingBaseline(Long id, LocalDateTime startedAt) {
        this.id = id;
        this.startedAt = startedAt;
    }

    public static DepartmentRankingBaseline start(Long id, LocalDateTime startedAt) {
        if (id == null || startedAt == null) {
            throw new IllegalArgumentException("학과 랭킹 기준 ID와 시작 시각은 필수입니다.");
        }
        return new DepartmentRankingBaseline(id, startedAt);
    }
}
