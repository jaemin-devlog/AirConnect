package univ.airconnect.iap.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IosTransactionVerifyRequest {

    @NotBlank
    private String signedTransactionInfo;

    private String transactionId;

    private String appAccountToken;
}


