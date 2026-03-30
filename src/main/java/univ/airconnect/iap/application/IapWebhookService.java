package univ.airconnect.iap.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapEvent;
import univ.airconnect.iap.dto.response.IapWebhookAckResponse;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;
import univ.airconnect.iap.repository.IapEventRepository;

import java.util.Map;

@Service
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
        saveEvent(IapStore.APPLE, "APPLE_SERVER_NOTIFICATION_V2", payload, null, null);
        return new IapWebhookAckResponse(true);
    }

    @Transactional
    public IapWebhookAckResponse ingestGoogleNotification(Map<String, Object> payload) {
        saveEvent(IapStore.GOOGLE, "GOOGLE_RTDN", payload, null, null);
        return new IapWebhookAckResponse(true);
    }

    private void saveEvent(IapStore store,
                           String eventType,
                           Map<String, Object> payload,
                           String transactionId,
                           String purchaseToken) {
        try {
            String raw = objectMapper.writeValueAsString(payload);
            iapEventRepository.save(IapEvent.create(
                    store,
                    eventType,
                    transactionId,
                    purchaseToken,
                    payloadSecurityUtil.sha256(raw),
                    payloadSecurityUtil.mask(raw)
            ));
        } catch (Exception ignored) {
            // webhook 수신 실패로 전체 API를 실패시키지 않기 위해 이벤트 저장 오류는 무시한다.
        }
    }
}

