package univ.airconnect.iap.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapEvent;
import univ.airconnect.iap.dto.response.IapWebhookAckResponse;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;
import univ.airconnect.iap.repository.IapEventRepository;

import java.util.Map;

@Service
@Slf4j
public class IapWebhookService {

    private final IapEventRepository iapEventRepository;
    private final PayloadSecurityUtil payloadSecurityUtil;
    private final ObjectMapper objectMapper;

    public IapWebhookService(IapEventRepository iapEventRepository,
                             PayloadSecurityUtil payloadSecurityUtil,
                             ObjectMapper objectMapper) {
        this.iapEventRepository = iapEventRepository;
        this.payloadSecurityUtil = payloadSecurityUtil;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IapWebhookAckResponse ingestAppleNotification(Map<String, Object> payload) {
        log.info("IAP webhook ingest started. store=APPLE, payloadKeys={}", payload.keySet());
        saveEvent(IapStore.APPLE, "APPLE_SERVER_NOTIFICATION_V2", payload, null, null);
        log.info("IAP webhook ingest completed. store=APPLE");
        return new IapWebhookAckResponse(true);
    }

    @Transactional
    public IapWebhookAckResponse ingestGoogleNotification(Map<String, Object> payload) {
        log.info("IAP webhook ingest started. store=GOOGLE, payloadKeys={}", payload.keySet());
        saveEvent(IapStore.GOOGLE, "GOOGLE_RTDN", payload, null, null);
        log.info("IAP webhook ingest completed. store=GOOGLE");
        return new IapWebhookAckResponse(true);
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
}

