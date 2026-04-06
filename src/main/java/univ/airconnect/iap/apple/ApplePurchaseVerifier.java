package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.iap.application.StorePurchaseVerifier;
import univ.airconnect.iap.application.StoreVerificationResult;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapProductPolicy;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.infrastructure.IapProperties;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.List;

@Component
@Slf4j
public class ApplePurchaseVerifier implements StorePurchaseVerifier {

    private final IapProperties iapProperties;
    private final PayloadSecurityUtil payloadSecurityUtil;
    private final UserRepository userRepository;
    private final AppleSignedTransactionVerifier appleSignedTransactionVerifier;

    public ApplePurchaseVerifier(IapProperties iapProperties,
                                 PayloadSecurityUtil payloadSecurityUtil,
                                 UserRepository userRepository,
                                 AppleSignedTransactionVerifier appleSignedTransactionVerifier) {
        this.iapProperties = iapProperties;
        this.payloadSecurityUtil = payloadSecurityUtil;
        this.userRepository = userRepository;
        this.appleSignedTransactionVerifier = appleSignedTransactionVerifier;
    }

    @Override
    public IapStore supports() {
        return IapStore.APPLE;
    }

    @Override
    public StoreVerificationResult verify(Long userId, Object request) {
        IosTransactionVerifyRequest req = (IosTransactionVerifyRequest) request;
        log.info("Apple verifier started. userId={}, transactionId={}", userId, req.getTransactionId());
        try {
            JsonNode payload = appleSignedTransactionVerifier.verifyAndExtractPayload(req.getSignedTransactionInfo());
            String bundleId = text(payload, "bundleId");
            String productId = text(payload, "productId");
            String transactionId = firstNotBlank(text(payload, "transactionId"), req.getTransactionId());
            String originalTransactionId = text(payload, "originalTransactionId");
            String appAccountToken = text(payload, "appAccountToken");
            String env = firstNotBlank(text(payload, "environment"), iapProperties.getApple().getEnvironment());
            boolean revoked = hasNonNull(payload, "revocationDate") || hasNonNull(payload, "revocationReason");
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IapException(IapErrorCode.IAP_UNAUTHORIZED));
            String issuedToken = user.ensureIosAppAccountToken();
            log.info("Apple verifier payload parsed. userId={}, transactionId={}, productId={}, env={}",
                    userId, transactionId, productId, env);

            if (bundleId == null || !bundleId.equals(iapProperties.getApple().getBundleId())) {
                throw new IapException(IapErrorCode.IAP_ENVIRONMENT_MISMATCH, "Apple bundleId 불일치");
            }
            validateConfiguredProduct(productId);
            if (transactionId == null || productId == null) {
                throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION);
            }
            if (req.getTransactionId() != null
                    && !req.getTransactionId().isBlank()
                    && !req.getTransactionId().equals(transactionId)) {
                throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "요청 transactionId 불일치");
            }
            if (appAccountToken == null || !issuedToken.equals(appAccountToken)) {
                log.warn("Apple verifier appAccountToken mismatch. userId={}, transactionId={}", userId, transactionId);
                throw new IapException(IapErrorCode.IAP_ACCOUNT_TOKEN_MISMATCH);
            }
            if (req.getAppAccountToken() != null
                    && !req.getAppAccountToken().isBlank()
                    && !req.getAppAccountToken().equals(appAccountToken)) {
                throw new IapException(IapErrorCode.IAP_ACCOUNT_TOKEN_MISMATCH, "요청 appAccountToken 불일치");
            }

            IapEnvironment payloadEnvironment = parseEnv(env);
            validateEnvironment(payloadEnvironment);

            StoreVerificationResult result = StoreVerificationResult.builder()
                    .store(IapStore.APPLE)
                    .productId(productId)
                    .transactionId(transactionId)
                    .originalTransactionId(originalTransactionId)
                    .appAccountToken(issuedToken)
                    .environment(payloadEnvironment)
                    .verificationHash(payloadSecurityUtil.sha256(req.getSignedTransactionInfo()))
                    .rawPayloadMasked(payloadSecurityUtil.mask(req.getSignedTransactionInfo()))
                    .valid(!revoked)
                    .transactionRevoked(revoked)
                    .build();
            log.info("Apple verifier completed. userId={}, transactionId={}, productId={}, hash={}",
                    userId, transactionId, productId, result.getVerificationHash());
            return result;
        } catch (IapException e) {
            log.warn("Apple verifier failed. userId={}, transactionId={}, errorCode={}, message={}",
                    userId, req.getTransactionId(), e.getErrorCode().getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Apple verifier parse failure. userId={}, transactionId={}, reason={}",
                    userId, req.getTransactionId(), e.getMessage());
            throw new IapException(IapErrorCode.IAP_APPLE_VERIFY_FAILED, "Apple signedTransactionInfo 파싱 실패");
        }
    }

    private void validateConfiguredProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IapException(IapErrorCode.IAP_INVALID_PRODUCT);
        }
        if (IapProductPolicy.fromProductId(productId) == null) {
            throw new IapException(IapErrorCode.IAP_INVALID_PRODUCT);
        }

        List<String> allowList = iapProperties.getApple().getAllowedProductIds();
        if (allowList == null) {
            return;
        }
        List<String> normalizedAllowList = allowList.stream()
                .filter(v -> v != null && !v.isBlank())
                .toList();
        if (normalizedAllowList.isEmpty()) {
            return;
        }

        if (!normalizedAllowList.contains(productId)) {
            throw new IapException(IapErrorCode.IAP_INVALID_PRODUCT, "허용되지 않은 productId");
        }
    }

    private String text(JsonNode node, String key) {
        if (node == null || !node.hasNonNull(key)) {
            return null;
        }
        return node.get(key).asText();
    }

    private String firstNotBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private boolean hasNonNull(JsonNode node, String key) {
        return node != null && node.hasNonNull(key);
    }

    private void validateEnvironment(IapEnvironment payloadEnvironment) {
        IapEnvironment configuredEnvironment = parseEnv(iapProperties.getApple().getEnvironment());
        if (configuredEnvironment == IapEnvironment.UNKNOWN || payloadEnvironment == IapEnvironment.UNKNOWN) {
            return;
        }
        if (configuredEnvironment != payloadEnvironment) {
            throw new IapException(IapErrorCode.IAP_ENVIRONMENT_MISMATCH, "Apple environment 불일치");
        }
    }

    private IapEnvironment parseEnv(String env) {
        if (env == null) {
            return IapEnvironment.UNKNOWN;
        }
        String normalized = env.trim().toUpperCase();
        if (normalized.contains("SANDBOX")) {
            return IapEnvironment.SANDBOX;
        }
        if (normalized.contains("PROD")) {
            return IapEnvironment.PRODUCTION;
        }
        return IapEnvironment.UNKNOWN;
    }
}
