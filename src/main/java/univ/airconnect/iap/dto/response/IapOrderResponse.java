package univ.airconnect.iap.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapOrderStatus;
import univ.airconnect.iap.domain.IapStore;

import java.time.OffsetDateTime;

@Getter
@Builder
public class IapOrderResponse {

    private Long id;
    private Long userId;
    private IapStore store;
    private String productId;
    private String transactionId;
    private String purchaseToken;
    private String orderId;
    private IapEnvironment environment;
    private IapOrderStatus status;
    private Integer grantedTickets;
    private Integer beforeTickets;
    private Integer afterTickets;
    private OffsetDateTime processedAt;
    private OffsetDateTime createdAt;
}

