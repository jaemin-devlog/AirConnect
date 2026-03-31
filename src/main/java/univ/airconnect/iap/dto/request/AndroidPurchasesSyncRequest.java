package univ.airconnect.iap.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AndroidPurchasesSyncRequest {

    @NotEmpty
    @Valid
    private List<AndroidSyncItem> purchases;

    @Getter
    @NoArgsConstructor
    public static class AndroidSyncItem {
        private String productId;
        private String purchaseToken;
        private String orderId;
        private String packageName;
    }
}

