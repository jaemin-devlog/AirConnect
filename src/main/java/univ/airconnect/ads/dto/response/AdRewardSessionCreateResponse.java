package univ.airconnect.ads.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AdRewardSessionCreateResponse {

    private String sessionKey;
    private Integer rewardAmount;
    private OffsetDateTime expiresAt;
}

