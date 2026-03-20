package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MatchingCandidateResponse {

    private Long userId;
    private String nickname;
    private String deptName;
    private String intro;
    private String profileImagePath;
}

