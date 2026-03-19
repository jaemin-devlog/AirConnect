package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.dto.response.UserMeResponse;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MatchingRecommendationResponse {

    private int count;
    private List<UserMeResponse> candidates;
    private Integer userTicketsRemaining;
}

