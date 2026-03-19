package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MatchingRequestsResponse {

    private int sentCount;
    private int receivedCount;
    private List<MatchingRequestResponse> sent;
    private List<MatchingRequestResponse> received;
}

