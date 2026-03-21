package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MatchingRecommendationResponse {

    private int count;
    private List<MatchingCandidateResponse> candidates;
    private Integer userTicketsRemaining;
}

