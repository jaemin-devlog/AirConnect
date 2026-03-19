package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.matching.domain.ConnectionStatus;

@Getter
@Builder
@AllArgsConstructor
public class MatchingResponseResponse {

    private Long connectionId;
    private Long targetUserId;
    private Long chatRoomId;
    private ConnectionStatus status;
}

