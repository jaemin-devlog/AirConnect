package univ.airconnect.compatibility.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompatibilityScoreDetail {

    private final CompatibilityFactor factor;
    private final int score;
    private final int maxScore;
    private final String reason;
    private final String caution;

    public double scoreRatio() {
        if (maxScore <= 0) {
            return 0;
        }
        return (double) score / maxScore;
    }
}
