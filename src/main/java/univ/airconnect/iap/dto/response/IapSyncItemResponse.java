package univ.airconnect.iap.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IapSyncItemResponse {

    private boolean success;
    private IapVerifyResponse result;
    private String errorCode;
    private String message;
}

