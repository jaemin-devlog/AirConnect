package univ.airconnect.iap.google;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.iap.application.StorePurchaseVerifier;
import univ.airconnect.iap.application.StoreVerificationResult;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.dto.request.AndroidPurchaseVerifyRequest;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.infrastructure.IapProperties;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;

@Component
@Slf4j
public class GooglePurchaseVerifier implements StorePurchaseVerifier {

    private final IapProperties iapProperties;
    private final GooglePlayApiClient googlePlayApiClient;
    private final PayloadSecurityUtil payloadSecurityUtil;

    public GooglePurchaseVerifier(IapProperties iapProperties,
                                  GooglePlayApiClient googlePlayApiClient,
                                  PayloadSecurityUtil payloadSecurityUtil) {
        this.iapProperties = iapProperties;
        this.googlePlayApiClient = googlePlayApiClient;
        this.payloadSecurityUtil = payloadSecurityUtil;
    }

    @Override
    public IapStore supports() {
        return IapStore.GOOGLE;
    }

    @Override
    public StoreVerificationResult verify(Long userId, Object request) {
        AndroidPurchaseVerifyRequest req = (AndroidPurchaseVerifyRequest) request;
        log.info("Google verifier started. userId={}, orderId={}, packageName={}",
                userId, req.getOrderId(), req.getPackageName());
        if (!iapProperties.getGoogle().isVerifyEnabled()) {
            log.warn("Google verifier blocked. verifyEnabled=false, userId={}", userId);
            throw new IapException(IapErrorCode.IAP_GOOGLE_VERIFY_FAILED, "Google verify 비활성화 상태입니다.");
        }

        String packageName = req.getPackageName();
        if (!packageName.equals(iapProperties.getGoogle().getPackageName())) {
            log.warn("Google verifier package mismatch. userId={}, requestPackage={}, expectedPackage={}",
                    userId, packageName, iapProperties.getGoogle().getPackageName());
            throw new IapException(IapErrorCode.IAP_ENVIRONMENT_MISMATCH, "packageName 불일치");
        }

        JsonNode node = googlePlayApiClient.verifyProductPurchase(packageName, req.getProductId(), req.getPurchaseToken());
        String purchaseState = node.path("purchaseStateContext").path("purchaseState").asText("PURCHASE_STATE_UNSPECIFIED");
        log.info("Google verifier API response parsed. userId={}, purchaseState={}", userId, purchaseState);
        if (!"PURCHASED".equalsIgnoreCase(purchaseState)) {
            log.warn("Google verifier invalid purchase state. userId={}, purchaseState={}", userId, purchaseState);
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "구매 상태가 유효하지 않습니다.");
        }

        JsonNode firstItem = node.path("productLineItem").isArray() && !node.path("productLineItem").isEmpty()
                ? node.path("productLineItem").get(0)
                : null;
        String productId = firstItem == null ? req.getProductId() : firstItem.path("productId").asText(req.getProductId());
        String orderId = node.path("orderId").asText(req.getOrderId());
        String payloadRaw = node.toString();

        StoreVerificationResult result = StoreVerificationResult.builder()
                .store(IapStore.GOOGLE)
                .productId(productId)
                .purchaseToken(req.getPurchaseToken())
                .orderId(orderId)
                .transactionId(orderId)
                .environment(IapEnvironment.PRODUCTION)
                .verificationHash(payloadSecurityUtil.sha256(payloadRaw))
                .rawPayloadMasked(payloadSecurityUtil.mask(payloadRaw))
                .valid(true)
                .build();
        log.info("Google verifier completed. userId={}, orderId={}, productId={}, hash={}",
                userId, orderId, productId, result.getVerificationHash());
        return result;
    }
}

