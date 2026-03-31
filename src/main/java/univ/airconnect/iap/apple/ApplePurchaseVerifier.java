package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.iap.application.StorePurchaseVerifier;
import univ.airconnect.iap.application.StoreVerificationResult;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.infrastructure.IapProperties;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Slf4j
public class ApplePurchaseVerifier implements StorePurchaseVerifier {

    private final ObjectMapper objectMapper;
    private final IapProperties iapProperties;
    private final PayloadSecurityUtil payloadSecurityUtil;
    private final UserRepository userRepository;

    public ApplePurchaseVerifier(ObjectMapper objectMapper,
                                 IapProperties iapProperties,
                                 PayloadSecurityUtil payloadSecurityUtil,
                                 UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.iapProperties = iapProperties;
        this.payloadSecurityUtil = payloadSecurityUtil;
        this.userRepository = userRepository;
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
            JsonNode payload = decodePayload(req.getSignedTransactionInfo());
            String bundleId = text(payload, "bundleId");
            String productId = text(payload, "productId");
            String transactionId = firstNotBlank(text(payload, "transactionId"), req.getTransactionId());
            String originalTransactionId = text(payload, "originalTransactionId");
            String appAccountToken = text(payload, "appAccountToken");
            String env = firstNotBlank(text(payload, "environment"), iapProperties.getApple().getEnvironment());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IapException(IapErrorCode.IAP_UNAUTHORIZED));
            String issuedToken = user.ensureIosAppAccountToken();
            log.info("Apple verifier payload parsed. userId={}, transactionId={}, productId={}, env={}",
                    userId, transactionId, productId, env);

            if (bundleId == null || !bundleId.equals(iapProperties.getApple().getBundleId())) {
                throw new IapException(IapErrorCode.IAP_ENVIRONMENT_MISMATCH, "Apple bundleId 불일치");
            }
            if (transactionId == null || productId == null) {
                throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION);
            }
            if (appAccountToken == null || !issuedToken.equals(appAccountToken)) {
                log.warn("Apple verifier appAccountToken mismatch. userId={}, transactionId={}", userId, transactionId);
                throw new IapException(IapErrorCode.IAP_ACCOUNT_TOKEN_MISMATCH);
            }

            StoreVerificationResult result = StoreVerificationResult.builder()
                    .store(IapStore.APPLE)
                    .productId(productId)
                    .transactionId(transactionId)
                    .originalTransactionId(originalTransactionId)
                    .appAccountToken(issuedToken)
                    .environment(parseEnv(env))
                    .verificationHash(payloadSecurityUtil.sha256(req.getSignedTransactionInfo()))
                    .rawPayloadMasked(payloadSecurityUtil.mask(req.getSignedTransactionInfo()))
                    .valid(true)
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

    private JsonNode decodePayload(String jws) throws Exception {
        String[] parts = jws.split("\\.");
        if (parts.length < 2) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION);
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return objectMapper.readTree(payloadJson);
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

