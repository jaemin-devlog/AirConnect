package univ.airconnect.iap.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AndroidPurchasesSyncRequest {

    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<@NotNull AndroidSyncItem> purchases;

    @Getter
    @NoArgsConstructor
    public static class AndroidSyncItem {
        @Size(max = 120)
        private String productId;
        @NotBlank
        @Size(max = 512)
        private String purchaseToken;
        @Size(max = 120)
        private String orderId;
        @NotBlank
        @Size(max = 255)
        private String packageName;
    }
}

