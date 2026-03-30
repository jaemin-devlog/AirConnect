package univ.airconnect.iap.application;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;

@Getter
@Builder
public class StoreVerificationResult {

    private IapStore store;
    private String productId;
    private String transactionId;
    private String originalTransactionId;
    private String purchaseToken;
    private String orderId;
    private String appAccountToken;
    private IapEnvironment environment;
    private String verificationHash;
    private String rawPayloadMasked;
    private boolean valid;
}

