package univ.airconnect.compatibility.domain;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompatibilityResult {

    private final Long myUserId;
    private final Long targetUserId;
    private final int score;
    private final CompatibilityGrade grade;
    private final MbtiCompatibilityTier mbtiTier;
    private final List<String> reasons;
    private final List<String> cautions;
    private final List<CompatibilityScoreDetail> details;
}
