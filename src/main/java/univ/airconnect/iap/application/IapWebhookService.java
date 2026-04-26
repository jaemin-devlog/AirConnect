package univ.airconnect.iap.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.iap.apple.AppleSignedTransactionVerifier;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapEvent;
import univ.airconnect.iap.dto.response.IapWebhookAckResponse;
import univ.airconnect.iap.infrastructure.IapProperties;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;
import univ.airconnect.iap.repository.IapEventRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class IapWebhookService {

    private final IapEventRepository iapEventRepository;
    private final PayloadSecurityUtil payloadSecurityUtil;
    private final ObjectMapper objectMapper;
    private final AppleSignedTransactionVerifier appleSignedTransactionVerifier;
    private final IapRefundService iapRefundService;
    private final IapProperties iapProperties;

    public IapWebhookService(IapEventRepository iapEventRepository,
                             PayloadSecurityUtil payloadSecurityUtil,
                             ObjectMapper objectMapper,
                             AppleSignedTransactionVerifier appleSignedTransactionVerifier,
                             IapRefundService iapRefundService,
                             IapProperties iapProperties) {
        this.iapEventRepository = iapEventRepository;
        this.payloadSecurityUtil = payloadSecurityUtil;
        this.objectMapper = objectMapper;
        this.appleSignedTransactionVerifier = appleSignedTransactionVerifier;
        this.iapRefundService = iapRefundService;
        this.iapProperties = iapProperties;
    }

    @Transactional
    public IapWebhookAckResponse ingestAppleNotification(Map<String, Object> payload) {
        log.info("IAP webhook ingest started. store=APPLE, payloadKeys={}", payload.keySet());
        try {
            String signedPayload = text(payload, "signedPayload");
            if (signedPayload == null || signedPayload.isBlank()) {
                saveEvent(IapStore.APPLE, "APPLE_SERVER_NOTIFICATION_V2", payload, null, null);
                return new IapWebhookAckResponse(true);
            }

            var envelope = appleSignedTransactionVerifier.verifyAndExtractPayload(signedPayload);
            String notificationType = text(envelope, "notificationType");
            var data = envelope.path("data");
            String bundleId = text(data, "bundleId");
            String signedTransactionInfo = text(data, "signedTransactionInfo");
            String transactionId = null;
            boolean revoked = false;

            if (signedTransactionInfo != null && !signedTransactionInfo.isBlank()) {
                var transactionPayload = appleSignedTransactionVerifier.verifyAndExtractPayload(signedTransactionInfo);
                transactionId = text(transactionPayload, "transactionId");
                revoked = hasNonNull(transactionPayload, "revocationDate") || hasNonNull(transactionPayload, "revocationReason");
            }

            saveEvent(IapStore.APPLE,
                    firstNonBlank(notificationType, "APPLE_SERVER_NOTIFICATION_V2"),
                    payload,
                    transactionId,
                    null);

            if (bundleId != null
                    && iapProperties.getApple().getBundleId() != null
                    && !iapProperties.getApple().getBundleId().equals(bundleId)) {
                log.warn("IAP Apple webhook ignored due to bundle mismatch. expected={}, actual={}",
                        iapProperties.getApple().getBundleId(), bundleId);
                return new IapWebhookAckResponse(true);
            }

            if (revoked && transactionId != null) {
                iapRefundService.refundAppleTransaction(transactionId, "apple_webhook:" + notificationType);
            }
            log.info("IAP webhook ingest completed. store=APPLE, notificationType={}, transactionId={}",
                    notificationType, transactionId);
            return new IapWebhookAckResponse(true);
        } catch (Exception e) {
            log.warn("IAP Apple webhook processing failed. reason={}", e.getMessage());
            return new IapWebhookAckResponse(false);
        }
    }

    @Transactional
    public IapWebhookAckResponse ingestGoogleNotification(Map<String, Object> payload) {
        log.info("IAP webhook ingest started. store=GOOGLE, payloadKeys={}", payload.keySet());
        try {
            Map<String, Object> developerNotification = extractGoogleDeveloperNotification(payload);
            String eventType = "GOOGLE_RTDN";
            String purchaseToken = null;

            if (developerNotification.containsKey("voidedPurchaseNotification")) {
                Map<String, Object> voided = asMap(developerNotification.get("voidedPurchaseNotification"));
                eventType = "GOOGLE_VOIDED_PURCHASE";
                purchaseToken = text(voided, "purchaseToken");
            } else if (developerNotification.containsKey("oneTimeProductNotification")) {
                Map<String, Object> oneTime = asMap(developerNotification.get("oneTimeProductNotification"));
                eventType = "GOOGLE_ONE_TIME_PRODUCT_" + firstNonBlank(text(oneTime, "notificationType"), "UNKNOWN");
                purchaseToken = text(oneTime, "purchaseToken");
            }

            saveEvent(IapStore.GOOGLE, eventType, developerNotification, null, purchaseToken);
            log.info("IAP webhook ingest completed. store=GOOGLE, eventType={}, purchaseTokenExists={}",
                    eventType, purchaseToken != null);
            return new IapWebhookAckResponse(true);
        } catch (Exception e) {
            log.warn("IAP Google webhook processing failed. reason={}", e.getMessage());
            return new IapWebhookAckResponse(false);
        }
    }

    private void saveEvent(IapStore store,
                           String eventType,
                           Map<String, Object> payload,
                           String transactionId,
                           String purchaseToken) {
        try {
            String raw = objectMapper.writeValueAsString(payload);
            String payloadHash = payloadSecurityUtil.sha256(raw);
            iapEventRepository.save(IapEvent.create(
                    store,
                    eventType,
                    transactionId,
                    purchaseToken,
                    payloadHash,
                    payloadSecurityUtil.mask(raw)
            ));
            log.info("IAP webhook event saved. store={}, eventType={}, payloadHash={}", store, eventType, payloadHash);
        } catch (Exception e) {
            log.warn("IAP webhook event save failed but ignored. store={}, eventType={}, reason={}",
                    store, eventType, e.getMessage());
            // webhook 수신 실패로 전체 API를 실패시키지 않기 위해 이벤트 저장 오류는 무시한다.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("expected map payload");
    }

    private Map<String, Object> extractGoogleDeveloperNotification(Map<String, Object> payload) throws Exception {
        Object messageObject = payload.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            return payload;
        }
        Object data = messageMap.get("data");
        if (!(data instanceof String encoded) || encoded.isBlank()) {
            return payload;
        }
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        return objectMapper.readValue(decoded, Map.class);
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String text(com.fasterxml.jackson.databind.JsonNode node, String key) {
        if (node == null || !node.hasNonNull(key)) {
            return null;
        }
        return node.get(key).asText();
    }

    private boolean hasNonNull(com.fasterxml.jackson.databind.JsonNode node, String key) {
        return node != null && node.hasNonNull(key);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
