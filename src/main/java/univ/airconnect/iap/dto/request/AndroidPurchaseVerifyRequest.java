package univ.airconnect.iap.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AndroidPurchaseVerifyRequest {

    private String productId;

    @NotBlank
    private String purchaseToken;

    private String orderId;

    @NotBlank
    private String packageName;

    private String purchaseTime;
}


