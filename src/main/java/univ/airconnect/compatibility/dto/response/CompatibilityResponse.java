package univ.airconnect.compatibility.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.compatibility.domain.CompatibilityGrade;
import univ.airconnect.compatibility.domain.CompatibilityResult;
import univ.airconnect.compatibility.domain.MbtiCompatibilityTier;

@Getter
@Builder
public class CompatibilityResponse {

    private Long myUserId;
    private Long targetUserId;
    private Integer score;
    private CompatibilityGrade grade;
    private MbtiCompatibilityTier mbtiTier;
    private List<String> reasons;
    private List<String> cautions;
    private String summary;

    public static CompatibilityResponse from(CompatibilityResult result, String summary) {
        return CompatibilityResponse.builder()
                .myUserId(result.getMyUserId())
                .targetUserId(result.getTargetUserId())
                .score(result.getScore())
                .grade(result.getGrade())
                .mbtiTier(result.getMbtiTier())
                .reasons(result.getReasons())
                .cautions(result.getCautions())
                .summary(summary)
                .build();
    }
}
