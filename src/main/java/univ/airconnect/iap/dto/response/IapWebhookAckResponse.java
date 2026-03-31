package univ.airconnect.iap.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class IapWebhookAckResponse {
    private boolean accepted;
}

