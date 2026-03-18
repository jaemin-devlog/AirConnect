package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MatchingConnectResponse {

    private Long chatRoomId;
    private Long targetUserId;
    private boolean alreadyConnected;
}

