package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.matching.domain.ConnectionStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MatchingRequestResponse {

    private Long connectionId;
    private Long userId;
    private String nickname;
    private String deptName;
    private Integer studentNum;
    private String intro;
    private String mbti;
    private String residence;
    private String profileImagePath;
    private ConnectionStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}

