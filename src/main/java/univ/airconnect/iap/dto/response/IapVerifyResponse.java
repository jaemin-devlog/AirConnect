package univ.airconnect.iap.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.iap.domain.GrantStatus;

import java.time.OffsetDateTime;

@Getter
@Builder
public class IapVerifyResponse {

    private String transactionId;
    private String purchaseToken;
    private String orderId;
    private String productId;
    private GrantStatus grantStatus;
    private Integer grantedTickets;
    private Integer beforeTickets;
    private Integer afterTickets;
    private String ledgerId;
    private OffsetDateTime processedAt;
}

