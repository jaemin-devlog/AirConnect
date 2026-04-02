package univ.airconnect.ads.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AdRewardCallbackResponse {

    private String sessionKey;
    private String transactionId;
    private String grantStatus;
    private Integer grantedTickets;
    private Integer beforeTickets;
    private Integer afterTickets;
    private String ledgerId;
    private OffsetDateTime processedAt;
}

