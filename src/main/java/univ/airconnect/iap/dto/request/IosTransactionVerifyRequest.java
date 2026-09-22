package univ.airconnect.iap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IosTransactionVerifyRequest {

    @NotBlank
    @Size(max = 65_536)
    private String signedTransactionInfo;

    @Size(max = 80)
    private String transactionId;

    @Size(max = 120)
    private String appAccountToken;
}


