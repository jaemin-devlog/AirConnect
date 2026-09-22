package univ.airconnect.iap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AndroidPurchaseVerifyRequest {

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

    @Size(max = 100)
    private String purchaseTime;
}


